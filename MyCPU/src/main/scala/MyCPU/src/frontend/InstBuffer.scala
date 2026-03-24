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
        // to if4
        val canEnq = Output(Bool())
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

    // memory
    val instMem = Mem(size, Vec(p(FETCH_WIDTH), UInt(p(DATA_WIDTH).W)))
    val branchMem = Mem(size, Vec(p(FETCH_WIDTH), new BranchPredInfo))
    val pcMem = Mem(size, Vec(p(FETCH_WIDTH), UInt(p(ADDR_WIDTH).W)))

    val validMem = RegInit(VecInit.fill(size)(VecInit.fill(p(FETCH_WIDTH))(false.B)))
    val hitMem   = RegInit(VecInit.fill(size)(false.B))
    val paMem    = Reg(Vec(size, new PhysicalAddress))

    val headPtr = RegInit(0.U(ptrWidth.W))
    val tailPtr = RegInit(0.U(ptrWidth.W))
    val freeCount = RegInit(size.U((ptrWidth + 1).W))

    val enqValid = io.if4_instValid_i.asUInt.orR && (freeCount =/= 0.U)
    val canEnq = freeCount =/= 0.U
    io.canEnq := canEnq

    when(enqValid) {
        instMem.write(tailPtr, io.if4_inst_i)
        branchMem.write(tailPtr, io.if4_branch_result_i)
        pcMem.write(tailPtr, io.if4_pc_i)

        validMem(tailPtr) := io.if4_instValid_i
        hitMem(tailPtr)   := io.if4_icache_hit_i
        paMem(tailPtr)    := io.if4_pa_i

        tailPtr := tailPtr + 1.U
    }

    val deqInst_r   = instMem.read(headPtr)
    val deqBranch_r = branchMem.read(headPtr)
    val deqPC_r     = pcMem.read(headPtr)

    val deqValid_r  = validMem(headPtr)
    val deqHit_r    = hitMem(headPtr)
    val deqPA_r     = paMem(headPtr)

    // current cycle hit
    val deq_fire = deqHit_r && !io.stall

    when(deq_fire) {
        headPtr := headPtr + 1.U
        freeCount := freeCount + 1.U - enqValid
    }

    for(i <- 0 until p(FETCH_WIDTH)) {
        io.if5_pc_o(i) := Mux(deq_fire, deqPC_r(i), 0.U)
        io.if5_instValid_o(i) := Mux(deq_fire, deqValid_r(i), false.B)
        io.if5_inst_o(i) := Mux(deq_fire, deqInst_r(i), 0.U)
        io.if5_branch_result_o(i) :=
            Mux(deq_fire, deqBranch_r(i), 0.U.asTypeOf(new BranchPredInfo))
    }

    def lineAddr(pa: PhysicalAddress) =
        pa.bits(p(ADDR_WIDTH)-1, log2Ceil(p(L1CacheLineLength) * 4))

    val matchVec = VecInit((0 until size).map { i =>
        validMem(i).asUInt.orR &&
        (lineAddr(paMem(i)) === lineAddr(io.refill_pa_i))
    })

    val hit = matchVec.asUInt.orR && io.refill_valid_i
    val hitIdx = PriorityEncoder(matchVec)

    when(hit) {
        instMem.write(hitIdx, io.refill_insts_i)
        hitMem(hitIdx) := true.B
    }

    when(io.flush) {
        headPtr := 0.U
        tailPtr := 0.U
        freeCount := size.U

        validMem.foreach(_.foreach(_ := false.B))
        hitMem.foreach(_ := false.B)
    }
}
