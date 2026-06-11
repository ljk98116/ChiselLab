package MyCPU.backend

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._

import org.chipsalliance.cde.config._
import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._
import MyCPU.middleend._

// used for Load RAW harvard mark, enq at ex stage
// only store LDU result
// when load reached ROB top, rob enabled, deq
// multi read, multi write
class LoadBuffer(size: Int)(implicit p: Parameters) extends Module {
    val bankNum = p(LDU_NUM)
    val bankBits = log2Ceil(bankNum)
    val ptrWidth = log2Ceil(size)
    val perBankSize = size / bankNum
    val rowWidth = log2Ceil(perBankSize)

    val io = IO(new Bundle {
        val flush = Input(Bool())
        // mem0
        val mem0_ldu_results = Input(Vec(p(STU_NUM), new AGUResult))
        // mem1
        val mem1_stu_results = Input(Vec(p(LDU_NUM), new AGUResult))
        // mem2
        val mem2_st_stalls = Input(Vec(p(STU_NUM), Bool()))
        val loadBufferRobEnables = Input(Vec(p(FETCH_WIDTH), new LoadQueueEnable))
        val robHeadPtr = Input(UInt(p(ROBID_WIDTH).W))
        val robTailPtr = Input(UInt(p(ROBID_WIDTH).W))
        val mem0_loadBufferCanEnq = Output(Bool())
        // output to rob, mark the load inst as RAW harvard pred err
        val mem2_loadBuffer_resps = Output(Vec(p(STU_NUM), new LoadRAWHarvardInfo))
    })

    val headPtr = RegInit(0.U(ptrWidth.W))
    val tailPtr = RegInit(0.U(ptrWidth.W))
    val freeCount = RegInit(size.U((ptrWidth + 1).W))

    val banks = Seq.fill(bankNum)(
        RegInit(VecInit(
            Seq.fill(perBankSize)(0.U.asTypeOf(new AGUResult))
        ))
    )

    val atRobTopVec = Seq.fill(bankNum)(
        RegInit(VecInit(
            Seq.fill(perBankSize)(false.B)
        ))
    )

    val canDeq = headPtr =/= tailPtr

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

    val deqMask = WireInit(VecInit(
        Seq.fill(bankNum)(false.B)
    ))

    val deqCount = WireInit(0.U((bankBits + 1).W))

    for(b <- 0 until bankNum) {
        deqMask(b) := canDeq && 
            atRobTopVec(b)(headPtr(ptrWidth - 1, bankBits)) &&
            banks(b)(headPtr(ptrWidth - 1, bankBits)).valid
    }

    mem0_enqCount := PopCount(mem0_ldu_mask)
    deqCount := PopCount(deqMask)

    mem0_ldu_prefix_sums(0) := 0.U
    for(i <- 1 until p(LDU_NUM)) {
        mem0_ldu_prefix_sums(i) := mem0_ldu_prefix_sums(i-1) + mem0_ldu_mask(i-1)
    }

    for(i <- 0 until p(LDU_NUM)) {
        when(io.mem0_ldu_results(i).valid) {
            mem0_ldu_enqs(mem0_ldu_prefix_sums(i)) := io.mem0_ldu_results(i)
        }
    }    

    io.mem0_loadBufferCanEnq := !(freeCount < mem0_enqCount)

    for(i <- 0 until p(LDU_NUM)) {
        val idx = tailPtr + i.U
        val bankId = idx(bankBits - 1, 0)
        val bankRow = idx(ptrWidth - 1, bankBits)
        for(b <- 0 until bankNum) {
            when(b.U === bankId && mem0_ldu_enqs(i).valid) {
                banks(b)(bankRow) := mem0_ldu_enqs(i)
                atRobTopVec(b)(bankRow) := VecInit((0 until p(FETCH_WIDTH)).map(j => 
                    io.loadBufferRobEnables(j).valid &&
                    io.loadBufferRobEnables(j).robLoc === mem0_ldu_enqs(i).robLoc    
                )).asUInt.orR
            }
        }
    }

    for(i <- 0 until p(LDU_NUM)) {
        val idx = headPtr + i.U
        val bankId = idx(bankBits - 1, 0)
        val bankRow = idx(ptrWidth - 1, bankBits)
        for(b <- 0 until bankNum) {
            when(b.U === bankId && banks(b)(bankRow).valid && deqMask(b)) {
                banks(b)(bankRow).valid := false.B
                atRobTopVec(b)(bankRow) := false.B
            }
        }        
    }

    val addr_los = WireInit(VecInit(
        Seq.fill(p(STU_NUM))(0.U((3 + p(FETCH_OFFSET)).W))
    ))

    val addr_masks = WireInit(VecInit(
        Seq.fill(p(STU_NUM))(0.U(p(DATA_WIDTH).W))
    ))

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

        val hasHit = WireInit(false.B)
        val newestHitIdx = WireInit(0.U(ptrWidth.W))

        val hitItem = WireInit(0.U.asTypeOf(new AGUResult))
        // need to do pipe here
        val mem2_st_results = PipeReg(io.mem1_stu_results(j), io.mem2_st_stalls(j), io.flush)
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
            val loadMask = item.mask << Cat(item.addr(p(FETCH_OFFSET) - 1, 0), "b000".U)

            val robLocFlag = WireInit(false.B)
            robLocFlag := io.mem1_st_results(j).robLoc < item.robLoc // store before load

            switch(Cat(robPtrFlag, robLocFlag)) {
                is("b00".U) {
                    beforeLoadMask(i) := false.B
                }
                is("b01".U){
                    beforeLoadMask(i) := true.B
                }
                is("b10".U) {
                    beforeLoadMask(i) := 
                        io.mem1_st_results(j).robLoc >= io.robHeadPtr &&
                        item.robLoc < io.robTailPtr
                }
                is("b11".U) {
                    beforeLoadMask(i) := !(io.mem1_st_results(j).robLoc >= io.robHeadPtr &&
                        item.robLoc < io.robTailPtr)
                }
            }

            originMask(i) := 
                ((addr_masks(j) & loadMask) === addr_masks(j)) && // load include store's data
                item.valid && 
                io.mem1_stu_results(j).valid &&
                io.mem1_stu_results(j).addr(p(DATA_WIDTH) - 1, p(FETCH_OFFSET)) === 
                    item.addr(p(DATA_WIDTH) - 1, p(FETCH_OFFSET))        

            mem2_beforeLoadMaskRes(i) := mem2_originMask(i) && mem2_beforeLoadMask(i)                
        }

        hasHit := mem2_beforeLoadMaskRes.asUInt.orR
        
    }

    tailPtr := Mux(io.mem0_loadBufferCanEnq, tailPtr + mem0_enqCount, tailPtr)
    headPtr := Mux(canDeq, headPtr + deqCount, headPtr)
    freeCount := freeCount - mem0_enqCount + deqCount
}