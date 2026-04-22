package MyCPU.backend

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

class SYSIQ(implicit p: Parameters) extends Module {
    val bankBits = log2Ceil(p(SYS_ALU_NUM))
    val perBankSize = p(SYSIQ_SIZE) / p(SYS_ALU_NUM)
    val ptrWidth = log2Ceil(p(STUIQ_SIZE))
    val perBankPtrWidth = log2Ceil(perBankSize)

    val io = IO(new Bundle {
        val flush = Input(Bool())
        val stall_from_backpipe = Input(Bool())
        val sys_uops = Input(Vec(p(FETCH_WIDTH), new uOp))
        val aluSelectRes = Input(Vec(p(ALU_NUM), new ALUIQEntry))
        val issue0_stall = Output(Bool())
        val busytableVec = Input(UInt((1 << p(PREG_WIDTH)).W))
        val sysiq_o = Output(Vec(p(SYS_ALU_NUM), new SYSIQEntry))        
    })

    // banks: SYS_ALU_NUM x perBankSize
    val banks = RegInit(VecInit(
        Seq.fill(p(SYS_ALU_NUM))(
            VecInit(Seq.fill(perBankSize)(0.U.asTypeOf(new STUIQEntry)))
        )
    ))

    val head = RegInit(0.U(ptrWidth.W))
    val tail = RegInit(0.U(ptrWidth.W))

    // head/tail stall
    io.issue0_stall := (tail + 1.U) === head 

    // --- Issue logic ---
    for (b <- 0 until p(STU_NUM)) {
        val rd_idx = head + b.U
        val headBankId = rd_idx(bankBits - 1, 0)
        val headBankRow = rd_idx(ptrWidth - 1, bankBits)

        val entry = banks(b)(headBankRow)

        io.sysiq_o(b) := entry
        io.sysiq_o(b).valid := entry.valid && (b.U === headBankId) && entry.rdy1 && entry.rdy2 && entry.atRobHead
    }       

    val numIssue = PopCount(io.sysiq_o.map(_.valid))
    val numEnqueue = PopCount(io.sys_uops.map(_.valid))
    
        
}