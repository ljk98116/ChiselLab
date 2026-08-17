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
    val ptrWidth = log2Ceil(p(SYSIQ_SIZE))
    val perBankPtrWidth = log2Ceil(perBankSize)
    val wakeUpPortNum = WakeUpQueuePortNum(p)

    val io = IO(new Bundle {
        val flush = Input(Bool())
        val stall_from_backpipe = Input(Bool())
        val sys_uops = Input(Vec(p(FETCH_WIDTH), new uOp))
        val issue0_stall = Output(Bool())
        val wakeup_signals = Input(Vec(wakeUpPortNum, new WakeUpQueueEntry))
        val busyTableVec = Input(UInt((1 << p(PREG_WIDTH)).W))
        val sysiq_o = Output(Vec(p(SYS_ALU_NUM), new SYSIQEntry))        
    })

    // banks: SYS_ALU_NUM x perBankSize
    val banks = RegInit(VecInit(
        Seq.fill(p(SYS_ALU_NUM))(
            VecInit(Seq.fill(perBankSize)(0.U.asTypeOf(new SYSIQEntry)))
        )
    ))

    val head = RegInit(0.U(ptrWidth.W))
    val tail = RegInit(0.U(ptrWidth.W))

    // head/tail stall
    io.issue0_stall := (tail + 1.U) === head 

    // --- Issue logic ---
    for (b <- 0 until p(SYS_ALU_NUM)) {
        val rd_idx = head + b.U
        val headBankId = rd_idx(bankBits - 1, 0)
        val headBankRow = rd_idx(ptrWidth - 1, bankBits)

        val entry = banks(b)(headBankRow)

        io.sysiq_o(b) := entry
        io.sysiq_o(b).valid := entry.valid && (b.U === headBankId) && entry.rdy1 && entry.rdy2
    }       

    val numIssue = PopCount(io.sysiq_o.map(_.valid))
    val numEnqueue = PopCount(io.sys_uops.map(_.valid))

    // --- ALU Wakeup precompute ---
    val aluWakeup_ps1 = Wire(Vec(p(SYSIQ_SIZE), Bool()))
    val aluWakeup_ps2 = Wire(Vec(p(SYSIQ_SIZE), Bool()))

    for (i <- 0 until p(SYSIQ_SIZE)) {
        aluWakeup_ps1(i) := io.wakeup_signals.map(a => a.HasRd && (a.pd === banks(i % p(SYS_ALU_NUM))(i / p(SYS_ALU_NUM)).ps1)).reduce(_ || _)
        aluWakeup_ps2(i) := io.wakeup_signals.map(a => a.HasRd && (a.pd === banks(i % p(SYS_ALU_NUM))(i / p(SYS_ALU_NUM)).ps2)).reduce(_ || _)
    }    

    // --- Enqueue logic ---
    for (w <- 0 until p(FETCH_WIDTH)) {
        when(io.sys_uops(w).valid) {
            val idx = tail + w.U
            val b = idx(bankBits - 1, 0)
            val row = idx(ptrWidth - 1, bankBits)

            val entry = WireInit(0.U.asTypeOf(new SYSIQEntry))
            entry := banks(b)(row)

            // update fields
            entry.HasRd := io.sys_uops(w).HasRd
            entry.HasRs1 := io.sys_uops(w).HasRs1
            entry.HasRs2 := io.sys_uops(w).HasRs2
            
            entry.branchPredInfo := io.sys_uops(w).branchPredInfo
            entry.funct3 := io.sys_uops(w).funct3
            entry.funct7 := io.sys_uops(w).funct7
            entry.imm := io.sys_uops(w).imm
            entry.old_pd := io.sys_uops(w).old_pd
            entry.opcode := io.sys_uops(w).opcode
            entry.pc := io.sys_uops(w).pc
            entry.pd := io.sys_uops(w).pd
            entry.ps1 := io.sys_uops(w).ps1
            entry.ps2 := io.sys_uops(w).ps2
            entry.rd := io.sys_uops(w).rd
            entry.rs1 := io.sys_uops(w).rs1
            entry.rs2 := io.sys_uops(w).rs2

            // ready signals
            entry.rdy1 := !io.busyTableVec(io.sys_uops(w).ps1) || aluWakeup_ps1(idx)
            entry.rdy2 := !io.busyTableVec(io.sys_uops(w).ps2) || aluWakeup_ps2(idx)

            entry.valid := true.B
        }
    }

    // --- Update existing entries ---
    for (b <- 0 until p(SYS_ALU_NUM)) {
        for (i <- 0 until perBankSize) {
            val entry = banks(b)(i)
            // Skip newly enqueued entries
            when(!entry.valid || entry.rdy1 && entry.rdy2) {
                entry.rdy1 := entry.rdy1 || !io.busyTableVec(entry.ps1) || aluWakeup_ps1((i * p(SYS_ALU_NUM) + b).U)
                entry.rdy2 := entry.rdy2 || !io.busyTableVec(entry.ps2) || aluWakeup_ps2((i * p(SYS_ALU_NUM) + b).U)
            }
        }
    }    
}