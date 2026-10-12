package MyCPU.backend

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._

import org.chipsalliance.cde.config._
import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._
import MyCPU.middleend._

class LoadBufferEntry(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val store_pc = UInt(p(ADDR_WIDTH).W) 
    val hasRAWHarvard = Bool()
    val aguResult = new AGUResult
    val atRobTop = Bool()
}

class LoadBufferPerBankFreeLocQueue(size: Int)(implicit p: Parameters) extends Module {
    val width = log2Ceil(size)
    val io = IO(new Bundle {
        val flush = Input(Bool())
        val deqEnable = Input(Bool())
        val enqEnable = Input(Bool())
        val enqId = Input(UInt(width.W))
        val deqId = Output(UInt(width.W))
        val deqValid = Output(Bool())
    })

    val headPtr = RegInit(0.U(width.W))
    val tailPtr = RegInit((size - 1).U(width.W))

    val ram = RegInit(VecInit(
        Seq.fill(size)(0.U(width.W))
    ))

    for(i <- 0 until size) {
        when(reset.asBool || io.flush) {
            ram(i) := i.U
        }
    }

    io.deqId := Mux(io.deqEnable && (headPtr =/= tailPtr), ram(headPtr), 0.U)
    io.deqValid := io.deqEnable && (headPtr =/= tailPtr)

    when((tailPtr + 1.U =/= headPtr) && io.enqEnable) {
        ram(tailPtr) := io.enqId
    }

    headPtr := Mux(io.flush, 0.U, headPtr + io.deqValid)
    tailPtr := Mux(io.flush, (size - 1).U, tailPtr + ((tailPtr + 1.U =/= headPtr) && io.enqEnable).asUInt)
}

// used for Load RAW harvard mark, enq at mem0 stage
// store LDU result and newest harvard store pc
// when load reached ROB top, rob enabled, update atRobTop status
// when mem5 load can deq, then deq the oldest item, release the slot
// multi read, multi write
class LoadBuffer(size: Int)(implicit p: Parameters) extends Module {
    val bankNum = p(LDU_NUM)
    val bankBits = log2Ceil(bankNum)
    val ptrWidth = log2Ceil(size)
    val perBankSize = size / bankNum
    val rowWidth = log2Ceil(perBankSize)

    assert(bankNum == p(LDU_NUM))
    val io = IO(new Bundle {
        val flush = Input(Bool())
        // mem0
        val mem0_ldu_results = Input(Vec(p(STU_NUM), new AGUResult))
        // mem1
        val mem1_stu_results = Input(Vec(p(LDU_NUM), new AGUResult))
        // mem2
        val mem2_st_stalls = Input(Vec(p(STU_NUM), Bool()))
        // mem5
        val mem5_ldu_deqs = Input(Vec(bankNum, new LoadWbQueueItem))
        // the rob top is load ?
        val loadBufferRobEnables = Input(Vec(p(FETCH_WIDTH), new LoadQueueEnable))
        val robHeadPtr = Input(UInt(p(ROBID_WIDTH).W))
        val robTailPtr = Input(UInt(p(ROBID_WIDTH).W))
        val mem0_loadBufferCanEnq = Output(Bool())
        // output to rob, mark the load inst as RAW harvard pred err
        val load_raw_harvards = Output(Vec(bankNum, new LoadRAWHarvardInfo))
    })

    val banks = Seq.fill(bankNum)(
        RegInit(VecInit(
            Seq.fill(perBankSize)(0.U.asTypeOf(new LoadBufferEntry))
        ))
    )

    val mem0_ldu_mask = VecInit(
        io.mem0_ldu_results.map(_.valid)
    )

    val mem0_ldu_prefix_sums = WireInit(VecInit(
        Seq.fill(p(LDU_NUM))(0.U(bankBits.W))
    ))

    val mem0_ldu_enqs = WireInit(VecInit(
        Seq.fill(p(LDU_NUM))(0.U.asTypeOf(new AGUResult))
    ))    

    val mem0_enqCount = WireInit(0.U((bankBits + 1).W))

    val addr_los = WireInit(VecInit(
        Seq.fill(p(STU_NUM))(0.U((3 + p(FETCH_OFFSET)).W))
    ))

    val addr_masks = WireInit(VecInit(
        Seq.fill(p(STU_NUM))(0.U(p(DATA_WIDTH).W))
    ))

    val freeIdQueue = Seq.fill(bankNum)(
        Module(new LoadBufferPerBankFreeLocQueue(perBankSize))
    )

    val load_enqueue_ids = WireInit(VecInit(
        Seq.fill(bankNum)(0.U(rowWidth.W))
    ))

    io.mem0_loadBufferCanEnq := VecInit((0 until p(LDU_NUM)).map(i => 
        (freeIdQueue(i).io.deqValid && mem0_ldu_enqs(i).valid) ||
        !mem0_ldu_enqs(i).valid   
    )).asUInt.andR

    // update banks
    val canDeqs = Seq.fill(bankNum)(
        Seq.fill(perBankSize)(WireInit(false.B))
    )

    val mem2_beforeLoadMaskResVec = WireInit(VecInit(
        Seq.fill(size)(
            VecInit(Seq.fill(p(STU_NUM))(false.B))
        )
    ))

    val mem2_st_results = VecInit((0 until p(STU_NUM)).map(j => 
        PipeReg(io.mem1_stu_results(j), io.mem2_st_stalls(j), io.flush)
    ))

    // real deq onehot mask
    val deqHitOHs = Seq.fill(bankNum)(
        Seq.fill(perBankSize)(WireInit(false.B))
    )

    // real deq hit
    val deqHasHits = Seq.fill(bankNum)(
        WireInit(false.B)
    )

    // real deq idx
    val deqHitBankRows = Seq.fill(bankNum)(
        WireInit(0.U(rowWidth.W))
    )

    // choose oldest canDeq perbank to deq and release slot
    // ageMat[i][j] = 1 <=> i is older than j
    val ageMats = Seq.fill(bankNum)(
        Seq.fill(perBankSize)(RegInit(0.U(perBankSize.W)))
    )

    mem0_ldu_prefix_sums(0) := 0.U
    for(i <- 1 until bankNum) {
        mem0_ldu_prefix_sums(i) := mem0_ldu_prefix_sums(i-1) + io.mem0_ldu_results(i-1).valid
    }

    for(b <- 0 until bankNum) {
        when(io.mem0_ldu_results(b).valid) {
            mem0_ldu_enqs(mem0_ldu_prefix_sums(b)) := io.mem0_ldu_results(b)
        }
        freeIdQueue(b).io.flush := io.flush
        freeIdQueue(b).io.deqEnable := mem0_ldu_enqs(b).valid
        load_enqueue_ids(b) := Mux(freeIdQueue(b).io.deqValid, freeIdQueue(b).io.deqId, 0.U)
        freeIdQueue(b).io.enqEnable := deqHasHits(b)
        freeIdQueue(b).io.enqId := deqHitBankRows(b)
    }

    for(i <- 0 until size) {
        val bankId = i & (bankNum - 1)
        val bankRow = i.U(ptrWidth - 1, bankBits)
        val bankRowInt = i >> bankBits

        val canDeqMask = WireInit(VecInit(
            Seq.fill(p(LDU_NUM))(false.B)
        ))

        val canDeq = WireInit(false.B)
        for(i <- 0 until p(LDU_NUM)) {
            canDeqMask(i) := io.mem5_ldu_deqs(i).valid &&
                io.mem5_ldu_deqs(i).robLoc === banks(bankId)(bankRow).aguResult.robLoc &&
                banks(bankId)(bankRow).valid && banks(bankId)(bankRow).atRobTop
        }
        canDeq := canDeqMask.asUInt.orR

        canDeqs(bankId)(bankRowInt) := canDeq

        when(freeIdQueue(bankId).io.deqValid && load_enqueue_ids(bankId) === i.U) {
            banks(bankId)(bankRow).aguResult := mem0_ldu_enqs(bankId)
            // ageMat[j][i] = 1
            for(j <- 0 until perBankSize) {
                ageMats(bankId)(j) := ageMats(bankId)(j) | (1 << bankRowInt).U
            }
        }.elsewhen(io.flush) {
            banks(bankId)(bankRow).aguResult := 0.U.asTypeOf(new AGUResult)
            for(j <- 0 until perBankSize) {
                ageMats(bankId)(j) := 0.U
            }            
        }

        val deqed = WireInit(false.B)
        deqed := deqHasHits(bankId) && (deqHitBankRows(bankId) === i.U)

        when(freeIdQueue(bankId).io.deqValid && load_enqueue_ids(bankId) === i.U) {
            banks(bankId)(bankRow).valid := mem0_ldu_enqs(bankId).valid
        }.elsewhen(io.flush || deqed) { // flush or deqed
            banks(bankId)(bankRow).valid := false.B
        }

        val mem2_beforeLoadMaskHitIdx = PriorityEncoder(mem2_beforeLoadMaskResVec(i))
        val mem2_beforeLoadMaskHasHit = mem2_beforeLoadMaskResVec(i).asUInt.orR

        when(mem2_beforeLoadMaskResVec(i).asUInt.orR) {
            banks(bankId)(bankRow).store_pc := mem2_st_results(mem2_beforeLoadMaskHitIdx).pc
            banks(bankId)(bankRow).hasRAWHarvard := true.B
        }.elsewhen(io.flush || deqed) { // flush or deqed
            banks(bankId)(bankRow).store_pc := 0.U
            banks(bankId)(bankRow).hasRAWHarvard := false.B
        }

        val atRobTopHitMask = WireInit(VecInit(
            Seq.fill(p(FETCH_WIDTH))(false.B)
        ))

        val atRobTopHasHit = WireInit(false.B)

        for(j <- 0 until p(FETCH_WIDTH)) {
            atRobTopHitMask(j) := 
                banks(bankId)(bankRow).aguResult.robLoc === io.loadBufferRobEnables(j).robLoc && 
                io.loadBufferRobEnables(j).valid && banks(bankId)(bankRow).valid
        }

        atRobTopHasHit := atRobTopHitMask.asUInt.orR

        when(atRobTopHasHit) {
            banks(bankId)(bankRow).atRobTop := true.B
        }

        // when deqed
        when(deqed) {
            ageMats(bankId)(bankRowInt) := 0.U
        }

        val robLocMatchMask = WireInit(VecInit(
            Seq.fill(p(FETCH_WIDTH))(false.B)
        ))

        val robLocHasMatch = WireInit(false.B)
        val robLocMatchIdx = WireInit(0.U(log2Ceil(p(FETCH_WIDTH)).W))

        for(j <- 0 until p(FETCH_WIDTH)) {
            robLocMatchMask(j) := io.loadBufferRobEnables(j).valid && 
                banks(bankId)(bankRow).valid && 
                io.loadBufferRobEnables(j).robLoc === banks(bankId)(bankRow).aguResult.robLoc
        }

        robLocHasMatch := robLocMatchMask.asUInt.orR
        robLocMatchIdx := PriorityEncoder(robLocMatchMask)
    }

    for(b <- 0 until bankNum) {
        for(r <- 0 until perBankSize) {
            deqHitOHs(b)(r) := canDeqs(b)(r) && ageMats(b)(r).asUInt.andR
        }
        deqHasHits(b) := deqHitOHs(b).reduce(_||_)
        deqHitBankRows(b) := PriorityEncoder(VecInit(deqHitOHs(b)))

        io.load_raw_harvards(b).valid := banks(b)(deqHitBankRows(b)).hasRAWHarvard
        io.load_raw_harvards(b).loadPC := banks(b)(deqHitBankRows(b)).aguResult.pc
        io.load_raw_harvards(b).storePC := banks(b)(deqHitBankRows(b)).store_pc
        io.load_raw_harvards(b).ldRobLoc := banks(b)(deqHitBankRows(b)).aguResult.robLoc
    }

    // get deq item perbank

    // get store inst robLoc -> rob head dis
    // get load inst robLoc -> rob head dis
    // 4 cases
    // robHead < robTail store robLoc < load robLoc, load err
    // robHead > robTail store robLoc < tail, load robLoc < tail -> store robLoc < load robLoc
    // robHead > robTail store robLoc > head, load robLoc > head -> store robLoc < load robLoc
    // robHead > robTail store robLoc > head, load robLoc < tail -> store robLoc > load robLoc
    // robHead > robTail store robLoc < tail, load robLoc > head -> store robLoc < load robLoc
    // robHead <= robTail, 4 cases, robLoc 1bit
    // 4 bit case table
    for(j <- 0 until p(STU_NUM)) {
        addr_los(j) := Cat(io.mem1_stu_results(j).addr(p(FETCH_OFFSET) - 1, 0), "b000".U) // addr_lo * 8
        addr_masks(j) := io.mem1_stu_results(j).mask << addr_los(j)

        val originMask = WireInit(VecInit(
            Seq.fill(size)(false.B)
        ))

        val beforeLoadMask = WireInit(VecInit(
            Seq.fill(size)(false.B)
        ))

        val hitItem = WireInit(0.U.asTypeOf(new AGUResult))
        // need to do pipe here
        val mem2_originMask = PipeReg(originMask, io.mem2_st_stalls(j), io.flush)
        val mem2_beforeLoadMask = PipeReg(beforeLoadMask, io.mem2_st_stalls(j), io.flush)
        val mem2_beforeLoadMaskRes = WireInit(VecInit(
            Seq.fill(size)(false.B)
        ))

        val robPtrFlag = WireInit(false.B)

        robPtrFlag := io.robHeadPtr >= io.robTailPtr

        for(i <- 0 until size) {
            val bankId = i & ((1 << bankBits) - 1)

            val bankRow = WireInit(0.U((ptrWidth - bankBits).W))
            bankRow := i.U(ptrWidth - 1, bankBits)

            val item = banks(bankId)(bankRow)
            val loadMask = item.aguResult.mask << Cat(item.aguResult.addr(p(FETCH_OFFSET) - 1, 0), "b000".U)

            val robLocFlag = WireInit(false.B)
            robLocFlag := io.mem1_stu_results(j).robLoc < item.aguResult.robLoc // store before load

            switch(Cat(robPtrFlag, robLocFlag)) {
                is("b00".U) {
                    beforeLoadMask(i) := false.B
                }
                is("b01".U){
                    beforeLoadMask(i) := true.B
                }
                is("b10".U) {
                    beforeLoadMask(i) := 
                        io.mem1_stu_results(j).robLoc >= io.robHeadPtr &&
                        item.aguResult.robLoc < io.robTailPtr
                }
                is("b11".U) {
                    beforeLoadMask(i) := !(io.mem1_stu_results(j).robLoc >= io.robHeadPtr &&
                        item.aguResult.robLoc < io.robTailPtr)
                }
            }

            originMask(i) := 
                ((addr_masks(j) & loadMask) === addr_masks(j)) && // load include store's data
                item.valid && 
                io.mem1_stu_results(j).valid &&
                io.mem1_stu_results(j).addr(p(DATA_WIDTH) - 1, p(FETCH_OFFSET)) === 
                    item.aguResult.addr(p(DATA_WIDTH) - 1, p(FETCH_OFFSET))        

            mem2_beforeLoadMaskRes(i) := mem2_originMask(i) && mem2_beforeLoadMask(i)     
            mem2_beforeLoadMaskResVec(i)(j) := mem2_beforeLoadMaskRes(i)          
        } 
    }
}