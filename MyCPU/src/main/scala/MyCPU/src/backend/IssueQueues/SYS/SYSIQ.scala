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
    val wakeUpPortNum = p(ALU_NUM) + p(MULT_NUM) + p(SYS_ALU_NUM)

    val io = IO(new Bundle {
        val flush = Input(Bool())
        val stalls = Input(Vec(p(SYS_ALU_NUM), Bool()))
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

    val canIssueVec = WireInit(VecInit(
        Seq.fill(p(SYS_ALU_NUM))(false.B)
    ))

    // --- Issue logic ---
    for (b <- 0 until p(SYS_ALU_NUM)) {
        val rd_idx = head + b.U
        val headBankId = rd_idx(bankBits - 1, 0)
        val headBankRow = rd_idx(ptrWidth - 1, bankBits)

        val entry = banks(b)(headBankRow)

        io.sysiq_o(b) := entry
        // io.sysiq_o(b).valid := entry.valid && (b.U === headBankId) && entry.rdy1 && entry.rdy2 && !io.stalls(b)
        canIssueVec(b) := (!entry.valid && !io.stalls(b)) || (entry.valid && (b.U === headBankId) && entry.rdy1 && entry.rdy2 && !io.stalls(b))
    }       

    val numIssue = PopCount(canIssueVec)
    val numEnqueue = PopCount(io.sys_uops.map(_.valid))

    // --- ALU Wakeup precompute ---
    val aluWakeup_ps1 = Wire(Vec(p(SYSIQ_SIZE), Bool()))
    val aluWakeup_ps2 = Wire(Vec(p(SYSIQ_SIZE), Bool()))

    printf("wakeupPortNum: %d\n", wakeUpPortNum.U)
    for(b <- 0 until p(SYS_ALU_NUM)) {
        for(i <- 0 until perBankSize) {
            val wakeUpMask1 = WireInit(VecInit(
                Seq.fill(wakeUpPortNum)(false.B)
            ))
            val wakeUpMask2 = WireInit(VecInit(
                Seq.fill(wakeUpPortNum)(false.B)
            ))  
            wakeUpMask1 := VecInit(io.wakeup_signals.map(a => a.HasRd && (a.pd === banks(b)(i).ps1)))
            wakeUpMask2 := VecInit(io.wakeup_signals.map(a => a.HasRd && (a.pd === banks(b)(i).ps2)))
            aluWakeup_ps1(b * perBankSize + i) := wakeUpMask1.reduce(_||_)
            aluWakeup_ps2(b * perBankSize + i) := wakeUpMask2.reduce(_||_)        
        }
    }

    // --- Enqueue logic ---
    for (w <- 0 until p(FETCH_WIDTH)) {
        when(io.sys_uops(w).valid) {
            val idx = tail + w.U
            val b = idx(bankBits - 1, 0)
            val row = idx(ptrWidth - 1, bankBits)

            // update fields
            banks(b)(row).HasRd := io.sys_uops(w).HasRd
            banks(b)(row).HasRs1 := io.sys_uops(w).HasRs1
            banks(b)(row).HasRs2 := io.sys_uops(w).HasRs2
            
            banks(b)(row).branchPredInfo := io.sys_uops(w).branchPredInfo
            banks(b)(row).funct3 := io.sys_uops(w).funct3
            banks(b)(row).funct7 := io.sys_uops(w).funct7
            banks(b)(row).imm := io.sys_uops(w).imm
            banks(b)(row).old_pd := io.sys_uops(w).old_pd
            banks(b)(row).opcode := io.sys_uops(w).opcode
            banks(b)(row).pc := io.sys_uops(w).pc
            banks(b)(row).pd := io.sys_uops(w).pd
            banks(b)(row).ps1 := io.sys_uops(w).ps1
            banks(b)(row).ps2 := io.sys_uops(w).ps2
            banks(b)(row).rd := io.sys_uops(w).rd
            banks(b)(row).rs1 := io.sys_uops(w).rs1
            banks(b)(row).rs2 := io.sys_uops(w).rs2

            // ready signals
            banks(b)(row).rdy1 := !io.busyTableVec(io.sys_uops(w).ps1) || aluWakeup_ps1(idx)
            banks(b)(row).rdy2 := !io.busyTableVec(io.sys_uops(w).ps2) || aluWakeup_ps2(idx)

            banks(b)(row).valid := true.B
        }
    }

    // --- Update existing entries ---
    for (b <- 0 until p(SYS_ALU_NUM)) {
        for (i <- 0 until perBankSize) {
            // Skip newly enqueued entries
            when(!banks(b)(i).valid || banks(b)(i).rdy1 && banks(b)(i).rdy2) {
                banks(b)(i).rdy1 := banks(b)(i).rdy1 || !io.busyTableVec(banks(b)(i).ps1) || aluWakeup_ps1((i * p(SYS_ALU_NUM) + b).U)
                banks(b)(i).rdy2 := banks(b)(i).rdy2 || !io.busyTableVec(banks(b)(i).ps2) || aluWakeup_ps2((i * p(SYS_ALU_NUM) + b).U)
            }
        }
    }    

    head := Mux(io.flush, 0.U, head + canIssueVec.asUInt.andR)
    tail := Mux(io.flush, 0.U, tail + VecInit(io.sys_uops.map(_.valid)).asUInt.orR)
}