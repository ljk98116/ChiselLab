package MyCPU.frontend

import chisel3._ 
import chisel3.util._ 
import MyCPU.config._ 
import MyCPU.io._ 
import MyCPU.utils._
import org.chipsalliance.cde.config._

// if4 enq, deq before decode, used as a pipelinestage
// has a valid pc, then enqValid is true
// check inst state, if icache miss, then wait refiller to inject insts
class InstBuffer(size: Int)(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val flush = Input(Bool())
        val stall = Input(Bool())
        // from if4
        val if4_pc_i = Input(Vec(p(FETCH_WIDTH), UInt(p(ADDR_WIDTH).W)))
        val if4_inst_i = Input(Vec(p(FETCH_WIDTH), UInt(p(DATA_WIDTH).W)))
        val if4_instValid_i = Input(Vec(p(FETCH_WIDTH), Bool()))
        val if4_icache_hit_i = Input(Bool())
        val if4_branch_result_i = Input(Vec(p(FETCH_WIDTH), new BranchPredInfo))
        val if4_pa_i = Input(new PhysicalAddress)
        // from icache related
        val refill_pa_i = Input(new PhysicalAddress)
        val refill_valid_i = Input(Bool())
        val refill_insts_i = Input(Vec(p(FETCH_WIDTH), UInt(p(DATA_WIDTH).W)))

        val if5_inst_o = Output(Vec(p(FETCH_WIDTH), UInt(p(DATA_WIDTH).W)))
        val if5_instValid_o = Output(Vec(p(FETCH_WIDTH), Bool()))
        val if5_branch_result_o = Output(Vec(p(FETCH_WIDTH), new BranchPredInfo))
        val if5_pc_o = Output(Vec(p(FETCH_WIDTH), UInt(p(ADDR_WIDTH).W)))
    })

    val ptrWidth = log2Ceil(size)
    val instMem = SyncReadMem(size, Vec(p(FETCH_WIDTH), UInt(p(DATA_WIDTH).W)))
    val branchResultMem = SyncReadMem(size, Vec(p(FETCH_WIDTH), new BranchPredInfo))
    val validMem = Mem(size, Vec(p(FETCH_WIDTH), Bool()))
    val hitMem = RegInit(VecInit(
        Seq.fill(size)(false.B)
    ))
    val pcMem = SyncReadMem(size, Vec(p(FETCH_WIDTH), UInt(p(ADDR_WIDTH).W)))
    val paMem = RegInit(VecInit(
        Seq.fill(size)(0.U.asTypeOf(new PhysicalAddress))
    ))

    val headPtr = RegInit(0.U(ptrWidth.W))
    val tailPtr = RegInit(0.U(ptrWidth.W))
    val freeCount = RegInit(size.U((ptrWidth + 1).W))

    val enqValid = io.if4_instValid_i.asUInt.orR && freeCount =/= 0.U
    when(enqValid) {
        instMem.write(tailPtr, io.if4_inst_i)
        validMem.write(tailPtr, io.if4_instValid_i)
        branchResultMem.write(tailPtr, io.if4_branch_result_i)
        pcMem.write(tailPtr, io.if4_pc_i)

        paMem(tailPtr) := io.if4_pa_i
        hitMem(tailPtr) := io.if4_icache_hit_i

        tailPtr := tailPtr + 1.U
        freeCount := freeCount - 1.U
    }

    val deqInst = instMem.read(headPtr)
    val deqValid = RegNext(validMem.read(headPtr))
    val deqBranchResult = branchResultMem.read(headPtr)
    val deqHit = hitMem(headPtr)
    val deqPC = pcMem.read(headPtr)
    val deqPA = RegNext(paMem(headPtr))

    headPtr := headPtr + deqHit
    freeCount := freeCount + deqHit

    val if5_deqHit = RegNext(deqHit)

    for(i <- 0 until p(FETCH_WIDTH)) {
        io.if5_pc_o(i) := Mux(if5_deqHit, deqPC(i), 0.U)
        io.if5_instValid_o(i) := Mux(if5_deqHit, deqValid(i), false.B)
        io.if5_inst_o(i) := Mux(if5_deqHit, deqInst(i), 0.U)
        io.if5_branch_result_o(i) := Mux(if5_deqHit, deqBranchResult(i), 0.U.asTypeOf(new BranchPredInfo))
    }

    // exist hit, update hitMem and instMem
    val tagHitVec = (0 until size).map(
        i => (paMem(i).GetPPN === io.refill_pa_i.GetPPN) && validMem.read(i.U).asUInt.orR && io.refill_valid_i
    )
    val tagHit = Cat(tagHitVec).orR
    val tagHitIndex = PriorityEncoder(tagHitVec)

    when(tagHit) {
        instMem.write(tagHitIndex, io.refill_insts_i)
    }
}
