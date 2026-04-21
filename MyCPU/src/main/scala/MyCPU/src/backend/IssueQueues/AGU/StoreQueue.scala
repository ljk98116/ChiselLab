package MyCPU.backend

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

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

  // banks: STU_NUM x perBankSize
  val banks = RegInit(VecInit(
    Seq.fill(p(STU_NUM))(
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

    io.issueItems(b) := entry
    io.issueItems(b).valid := entry.valid && (b.U === headBankId) && entry.rdy1 && entry.rdy2 && entry.atRobHead
  }

  val numIssue = PopCount(io.issueItems.map(_.valid))
  val numEnqueue = PopCount(io.store_uops.map(_.valid))

  head := Mux(io.flush, 0.U, head + numIssue)
  tail := Mux(io.flush, 0.U, Mux(io.issue0_stall, tail, tail + numEnqueue))

  // --- ALU Wakeup precompute ---
  val aluWakeup_ps1 = Wire(Vec(p(STUIQ_SIZE), Bool()))
  val aluWakeup_ps2 = Wire(Vec(p(STUIQ_SIZE), Bool()))

  for (i <- 0 until p(STUIQ_SIZE)) {
    aluWakeup_ps1(i) := io.aluSelectRes.map(a => a.HasRd && (a.pd === banks(i % p(STU_NUM))(i / p(STU_NUM)).ps1)).reduce(_ || _)
    aluWakeup_ps2(i) := io.aluSelectRes.map(a => a.HasRd && (a.pd === banks(i % p(STU_NUM))(i / p(STU_NUM)).ps2)).reduce(_ || _)
  }

  // --- Enqueue logic ---
  for (w <- 0 until p(FETCH_WIDTH)) {
    when(io.store_uops(w).valid) {
      val idx = tail + w.U
      val b = idx(bankBits - 1, 0)
      val row = idx(ptrWidth - 1, bankBits)

      val entry = WireInit(0.U.asTypeOf(new STUIQEntry))
      entry := banks(b)(row)

      // update fields
      entry.HasRd := io.store_uops(w).HasRd
      entry.HasRs1 := io.store_uops(w).HasRs1
      entry.HasRs2 := io.store_uops(w).HasRs2
      entry.LDSpecExec := io.store_uops(w).LDSpecExec
      entry.branchPredInfo := io.store_uops(w).branchPredInfo
      entry.funct3 := io.store_uops(w).funct3
      entry.funct7 := io.store_uops(w).funct7
      entry.imm := io.store_uops(w).imm
      entry.old_pd := io.store_uops(w).old_pd
      entry.opcode := io.store_uops(w).opcode
      entry.pc := io.store_uops(w).pc
      entry.pd := io.store_uops(w).pd
      entry.ps1 := io.store_uops(w).ps1
      entry.ps2 := io.store_uops(w).ps2
      entry.rd := io.store_uops(w).rd
      entry.rob_id := io.store_uops(w).rob_id
      entry.rs1 := io.store_uops(w).rs1
      entry.rs2 := io.store_uops(w).rs2

      // ready signals
      entry.rdy1 := !io.busyTableVec(io.store_uops(w).ps1) || aluWakeup_ps1(idx)
      entry.rdy2 := !io.busyTableVec(io.store_uops(w).ps2) || aluWakeup_ps2(idx)

      // at ROB head
      val atRobHead = Wire(Bool())
      atRobHead := VecInit(io.robEnable.map(r => r.valid && r.robId === entry.rob_id)).reduce(_ || _)
      entry.atRobHead := atRobHead

      entry.valid := true.B
    }
  }

  // --- Update existing entries ---
  for (b <- 0 until p(STU_NUM)) {
    for (i <- 0 until perBankSize) {
      val entry = banks(b)(i)
      // Skip newly enqueued entries
      when(!entry.valid || entry.rdy1 && entry.rdy2) {
        entry.rdy1 := entry.rdy1 || !io.busyTableVec(entry.ps1) || aluWakeup_ps1((i * p(STU_NUM) + b).U)
        entry.rdy2 := entry.rdy2 || !io.busyTableVec(entry.ps2) || aluWakeup_ps2((i * p(STU_NUM) + b).U)
        val atRobHead = io.robEnable.map(r => r.valid && r.robId === entry.rob_id).reduce(_ || _)
        entry.atRobHead := entry.atRobHead || atRobHead
      }
    }
  }
}