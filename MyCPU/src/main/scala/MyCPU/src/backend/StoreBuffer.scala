package MyCPU.backend

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

// when dcache write ready, deq, drop head
class StoreBuffer(size: Int)(implicit p: Parameters) extends Module {
    val bankNum = p(STU_NUM)
    val bankBits = log2Ceil(bankNum)
    val ptrWidth = log2Ceil(size)
    val perBankSize = size / bankNum

    val io = IO(new Bundle {
        val flush = Input(Bool())
        // mem0
        val mem0_stu_results = Input(Vec(p(STU_NUM), new AGUResult))
        // mem1
        val mem1_ldu_results = Input(Vec(p(LDU_NUM), new AGUResult))
        // mem2
        val mem2_ld_stalls = Input(Vec(p(LDU_NUM), Bool()))
        // if all banks are ready, then storeBuffer deqs
        val storeBufferDeqEnables = Input(Vec(p(STU_NUM), Bool()))
        val storeBufferDeqs = Output(Vec(p(STU_NUM), new AGUResult))
        val mem0_storeBufferCanEnq = Output(Bool())
        val mem2_storeBuffer_resps = Output(Vec(p(LDU_NUM), new StoreBufferRespInfo))
    })

    val banks = Seq.fill(bankNum)(
        RegInit(VecInit(
            Seq.fill(perBankSize)(0.U.asTypeOf(new AGUResult))
        ))
    )

    val headPtr = RegInit(0.U(ptrWidth.W))
    val tailPtr = RegInit(0.U(ptrWidth.W))
    val freeCount = RegInit(size.U((ptrWidth + 1).W))

    val canDeq = io.storeBufferDeqEnables.asUInt.andR &&
        headPtr =/= tailPtr

    val mem0_stu_mask = VecInit(
        io.mem0_stu_results.map(_.valid)
    )

    val mem0_stu_prefix_sums = WireInit(VecInit(
        Seq.fill(p(STU_NUM))(0.U(bankBits.W))
    ))

    val mem0_stu_enqs = WireInit(VecInit(
        Seq.fill(p(STU_NUM))(0.U.asTypeOf(new AGUResult))
    ))

    val mem0_enqCount = WireInit(0.U((bankBits + 1).W))
    val deqCount = WireInit(0.U((bankBits + 1).W))
    val deqMask = WireInit(VecInit(
        Seq.fill(bankNum)(false.B)
    ))

    mem0_stu_prefix_sums(0) := 0.U
    for(i <- 1 until p(STU_NUM)) {
        mem0_stu_prefix_sums(i) := mem0_stu_prefix_sums(i-1) + mem0_stu_mask(i-1)
    }

    for(i <- 0 until p(STU_NUM)) {
        when(io.mem0_stu_results(i).valid) {
            mem0_stu_enqs(mem0_stu_prefix_sums(i)) := io.mem0_stu_results(i)
        }
    }

    mem0_enqCount := PopCount(mem0_stu_mask)
    deqCount := PopCount(deqMask)

    io.mem0_storeBufferCanEnq := !(freeCount < mem0_enqCount)
    
    for(i <- 0 until p(STU_NUM)) {
        val idx = tailPtr + i.U
        val bankId = idx(bankBits - 1, 0)
        val bankRow = idx(ptrWidth - 1, bankBits)
        for(b <- 0 until bankNum) {
            when(b.U === bankId && mem0_stu_enqs(i).valid) {
                banks(b)(bankRow) := mem0_stu_enqs(i)
            }
        }
    }
    
    for(i <- 0 until p(STU_NUM)) {
        val idx = headPtr + i.U
        val bankId = idx(bankBits - 1, 0)
        val bankRow = idx(ptrWidth - 1, bankBits)
        io.storeBufferDeqs(i) := 0.U.asTypeOf(new AGUResult)
        for(b <- 0 until bankNum) {
            when(b.U === bankId && banks(b)(bankRow).valid && canDeq) {
                io.storeBufferDeqs(i) := banks(b)(bankRow)
                deqMask(b) := true.B
                banks(b)(bankRow).valid := false.B
            }
        }
    }

    // deal load raw judge, store write include load read
    // storebuffer
    // st   addr_lo  mask       addr_lo mask
    //      0        0xffffffff 0       0x0000ffff
    // ld   addr_lo  mask       
    //      0        0xffffffff 0       0x0000xxff
    //      1        0x0000xxff 
    //      2        0x0000xxff 1       0x000000ff
    //      3        0x000000ff 
    // ld addr_hi addr_low 
    // ld's mask << (8 * ld_addr_lo) & st mask << (8 * st_addr_lo) != 0, use store data, 
    // ld's mask << (8 * ld_addr_lo) & st mask & store's wdata is target result
    // must find the newest hit one
    // tail > head, use [tail - 1: head]
    // tail < head, use Cat([tail-1, 0][size -1, head]) as final mask
    val addr_los = WireInit(VecInit(
        Seq.fill(p(LDU_NUM))(0.U((3 + p(FETCH_OFFSET)).W))
    ))

    val addr_masks = WireInit(VecInit(
        Seq.fill(p(LDU_NUM))(0.U(p(DATA_WIDTH).W))
    ))

    for(j <- 0 until p(LDU_NUM)) {
        addr_los(j) := Cat(io.mem1_ldu_results(j).addr(p(FETCH_OFFSET) - 1, 0), "b000".U) // addr_lo * 8
        addr_masks(j) := io.mem1_ldu_results(j).mask << addr_los(j)

        val originMask = WireInit(VecInit(
            Seq.fill(size)(false.B)
        ))

        val belowTailMask = WireInit(VecInit(
            Seq.fill(size)(false.B)
        ))

        val aboveTailMask = WireInit(VecInit(
            Seq.fill(size)(false.B)
        ))

        val belowTailMaskSeq = Seq.fill(size)(WireInit(false.B))
        val aboveTailMaskSeq = Seq.fill(size)(WireInit(false.B))
        
        val hasAboveHit = WireInit(false.B)
        val hasBelowHit = WireInit(false.B)

        val newestHitIdx = WireInit(0.U(ptrWidth.W))

        val hitItem = WireInit(0.U.asTypeOf(new AGUResult))
        // need to do pipe here
        val mem2_hasAboveHit = PipeReg(hasAboveHit, false.B, io.flush)
        val mem2_hasBelowHit = PipeReg(hasBelowHit, false.B, io.flush)
        val mem2_addr_masks = PipeReg(addr_masks, false.B, io.flush)
        val mem2_hitItem = PipeReg(hitItem, false.B, io.flush)
        val mem2_ld_result = PipeReg(io.mem1_ldu_results(j), io.mem2_ld_stalls(j), io.flush)

        for(i <- 0 until size) {
            val bankId = i & ((1 << bankBits) - 1)

            val bankRow = WireInit(0.U((ptrWidth - bankBits).W))
            bankRow := i.U(ptrWidth - 1, bankBits)

            val item = banks(bankId)(bankRow)
            val storeMask = item.mask << Cat(item.addr(p(FETCH_OFFSET) - 1, 0), "b000".U)
            originMask(i) := 
                ((addr_masks(j) & storeMask) === addr_masks(j)) && 
                item.valid && 
                io.mem1_ldu_results(j).valid &&
                io.mem1_ldu_results(j).addr(p(DATA_WIDTH) - 1, p(FETCH_OFFSET)) === 
                    item.addr(p(DATA_WIDTH) - 1, p(FETCH_OFFSET))

            belowTailMaskSeq(i) := originMask(i) && (i.U < tailPtr) && 
            (
                (headPtr <= tailPtr && i.U >= headPtr) || headPtr > tailPtr
            )
            aboveTailMaskSeq(i) := originMask(i) && !(i.U < tailPtr) &&
            (
                (headPtr > tailPtr && i.U >= headPtr) || headPtr <= tailPtr
            )
        }

        aboveTailMask := aboveTailMaskSeq.reverse // reverse above
        belowTailMask := belowTailMaskSeq.reverse

        hasAboveHit := aboveTailMask.asUInt.orR
        hasBelowHit := belowTailMask.asUInt.orR

        newestHitIdx := Mux(
            hasBelowHit,
            (size - 1).U - PriorityEncoder(belowTailMask),
            Mux(hasAboveHit, (size - 1).U - PriorityEncoder(aboveTailMask), 0.U)
        )

        for(b <- 0 until bankNum) {
            when(b.U === newestHitIdx(bankBits - 1, 0)) {
                hitItem := banks(b)(newestHitIdx(ptrWidth - 1, bankBits))
            }
        }

        io.mem2_storeBuffer_resps(j).valid := mem2_hasAboveHit || mem2_hasBelowHit

        val ldData = mem2_hitItem.wdata & 
            mem2_addr_masks(j) & 
            mem2_hitItem.mask << Cat(mem2_hitItem.addr(p(FETCH_OFFSET) - 1, 0), "b000".U)

        when(mem2_ld_result.mask === "h000000FF".U) {
            io.mem2_storeBuffer_resps(j).data := Mux(
                mem2_ld_result.is_sign_ext,
                Cat(Fill(24, ldData(7)), ldData(7, 0)),
                ldData
            )
        }.elsewhen(mem2_ld_result.mask === "h0000FFFF".U) {
            io.mem2_storeBuffer_resps(j).data := Mux(
                mem2_ld_result.is_sign_ext,
                Cat(Fill(16, ldData(15)), ldData(15, 0)),
                ldData
            )
        }.otherwise {
            io.mem2_storeBuffer_resps(j).data := ldData
        }
    }

    tailPtr := Mux(io.mem0_storeBufferCanEnq, tailPtr + mem0_enqCount, tailPtr)
    headPtr := Mux(canDeq, headPtr + deqCount, headPtr)
    freeCount := freeCount - mem0_enqCount + deqCount
}