package MyCPU.backend

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

// FIFO Impl, banked Impl
class StoreQueue(implicit p: Parameters) extends Module {

    val bankBits = log2Ceil(p(STU_NUM))
    val perBankSize = p(STUIQ_SIZE) / p(STU_NUM)
    val ptrWidth = log2Ceil(p(STUIQ_SIZE))
    val perBankPtrWidth = log2Ceil(perBankSize)

    val io = IO(new Bundle {
        val flush = Input(Bool())
        val stall_from_backpipe = Input(Bool())
        val store_uops = Input(Vec(p(FETCH_WIDTH), new uOp))
        val busyTableVec = Input(UInt((1 << p(PREG_WIDTH)).W))
        val aluSelectRes = Input(Vec(p(ALU_NUM), new ALUIQEntry))
        val robEnable = Input(Vec(p(FETCH_WIDTH), new StoreQueueEnable))
        val issueItems = Output(Vec(p(STU_NUM), new STUIQEntry))
        val issue0_stall = Output(Bool())
    })

    val banks = Seq.fill(p(STU_NUM))(
        RegInit(VecInit(
            Seq.fill(perBankSize)(0.U.asTypeOf(new STUIQEntry))
        ))
    )

    val head = RegInit(0.U(ptrWidth.W))
    val tail = RegInit(0.U(ptrWidth.W))

    io.issue0_stall := (tail + 1.U) === head

    head := Mux(io.flush, 0.U, head + io.issueItems.map(_.valid.asUInt).reduce(_ + _))
    tail := Mux(io.flush, 0.U, Mux(io.issue0_stall, tail, tail + PopCount(io.store_uops.map(_.valid))))

    for(b <- 0 until p(STU_NUM)) {
        val rd_idx = head + b.U
        val headBankId = rd_idx(bankBits - 1, 0)
        val headBankRow = rd_idx(ptrWidth - 1, bankBits)

        io.issueItems(b) := banks(b)(headBankRow)
        io.issueItems(b).valid := 
            banks(b)(headBankRow).valid && 
            (b.U === headBankId) && 
            banks(b)(headBankRow).rdy1 &&
            banks(b)(headBankRow).rdy2 &&
            banks(b)(headBankRow).atRobHead
        
        for(i <- 0 until perBankSize) {
            val enqId = (i.U - tail)(log2Ceil(p(FETCH_WIDTH)) - 1, 0)
            val enqValid = enqId < p(FETCH_WIDTH).U
            val atRobHead = WireInit(false.B)

            atRobHead := VecInit((0 until p(FETCH_WIDTH)).map(
                j => banks(b)(i).valid && (banks(b)(i).rob_id === io.robEnable(j).robId) && io.robEnable(j).valid
            )).asUInt.orR

            val aluWakeUp1 = VecInit((0 until p(ALU_NUM)).map(
                j => io.aluSelectRes(j).HasRd && (io.aluSelectRes(j).pd === banks(b)(i).ps1)
            )).asUInt.orR

            val aluWakeUp2 = VecInit((0 until p(ALU_NUM)).map(
                j => io.aluSelectRes(j).HasRd && (io.aluSelectRes(j).pd === banks(b)(i).ps2)
            )).asUInt.orR

            when(enqValid && io.store_uops(enqId).valid) {
                banks(b)(i).HasRd := io.store_uops(enqId).HasRd
                banks(b)(i).HasRs1 := io.store_uops(enqId).HasRs1
                banks(b)(i).HasRs2 := io.store_uops(enqId).HasRs2
                banks(b)(i).LDSpecExec := io.store_uops(enqId).LDSpecExec
                banks(b)(i).branchPredInfo := io.store_uops(enqId).branchPredInfo
                banks(b)(i).funct3 := io.store_uops(enqId).funct3
                banks(b)(i).funct7 := io.store_uops(enqId).funct7
                banks(b)(i).imm := io.store_uops(enqId).imm
                banks(b)(i).old_pd := io.store_uops(enqId).old_pd
                banks(b)(i).opcode := io.store_uops(enqId).opcode
                banks(b)(i).pc := io.store_uops(enqId).pc
                banks(b)(i).pd := io.store_uops(enqId).pd
                banks(b)(i).ps1 := io.store_uops(enqId).ps1
                banks(b)(i).ps2 := io.store_uops(enqId).ps2
                banks(b)(i).rd := io.store_uops(enqId).rd
                banks(b)(i).rdy1 := io.busyTableVec(io.store_uops(enqId).ps1) || aluWakeUp1
                banks(b)(i).rdy2 := io.busyTableVec(io.store_uops(enqId).ps2) || aluWakeUp2
                banks(b)(i).rob_id := io.store_uops(enqId).rob_id
                banks(b)(i).rs1 := io.store_uops(enqId).rs1
                banks(b)(i).rs2 := io.store_uops(enqId).rs2
                banks(b)(i).atRobHead := atRobHead
            }.otherwise {

                banks(b)(i).rdy1 := banks(b)(i).rdy1 || io.busyTableVec(banks(b)(i).ps1) || aluWakeUp1 
                banks(b)(i).rdy2 := banks(b)(i).rdy2 || io.busyTableVec(banks(b)(i).ps2) || aluWakeUp2
                banks(b)(i).atRobHead := banks(b)(i).atRobHead || atRobHead
            }
        }
    }
}