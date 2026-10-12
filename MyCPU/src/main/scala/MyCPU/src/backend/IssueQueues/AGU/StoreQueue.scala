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

  val wakeUpPortNum = WakeUpQueuePortNum(p)
  val io = IO(new Bundle {
    val flush = Input(Bool())
    val stalls = Input(Vec(p(STU_NUM), Bool()))
    val store_uops = Input(Vec(p(FETCH_WIDTH), new uOp))
    val busyTableVec = Input(UInt((1 << p(PREG_WIDTH)).W))
    val wakeup_signals = Input(Vec(wakeUpPortNum, new WakeUpQueueEntry))
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
  val canIssueVec = WireInit(VecInit(
    Seq.fill(p(STU_NUM))(false.B)
  ))
  // head/tail stall
  io.issue0_stall := (tail + 1.U) === head

  // --- Issue logic ---
  for (b <- 0 until p(STU_NUM)) {
    val rd_idx = head + b.U
    val headBankId = rd_idx(bankBits - 1, 0)
    val headBankRow = rd_idx(ptrWidth - 1, bankBits)

    io.issueItems(b) := banks(b)(headBankRow)
    canIssueVec(b) := banks(b)(headBankRow).valid && (b.U === headBankId) && banks(b)(headBankRow).rdy1 && 
      banks(b)(headBankRow).rdy2 && banks(b)(headBankRow).atRobHead && ! io.stalls(b)
  }

  val numIssue = PopCount(io.issueItems.map(_.valid))
  val numEnqueue = PopCount(io.store_uops.map(_.valid))

  head := Mux(io.flush, 0.U, head + numIssue)
  tail := Mux(io.flush, 0.U, Mux(io.issue0_stall, tail, tail + numEnqueue))

  // --- ALU Wakeup precompute ---
  val aluWakeup_ps1 = Wire(Vec(p(STUIQ_SIZE), Bool()))
  val aluWakeup_ps2 = Wire(Vec(p(STUIQ_SIZE), Bool()))

  for (i <- 0 until p(STUIQ_SIZE)) {
    aluWakeup_ps1(i) := io.wakeup_signals.map(a => a.HasRd && (a.pd === banks(i % p(STU_NUM))(i / p(STU_NUM)).ps1)).reduce(_ || _)
    aluWakeup_ps2(i) := io.wakeup_signals.map(a => a.HasRd && (a.pd === banks(i % p(STU_NUM))(i / p(STU_NUM)).ps2)).reduce(_ || _)
  }

  // --- Enqueue logic ---
  for (w <- 0 until p(FETCH_WIDTH)) {
    when(io.store_uops(w).valid) {
      val idx = tail + w.U
      val b = idx(bankBits - 1, 0)
      val row = idx(ptrWidth - 1, bankBits)

      // update fields
      banks(b)(row).HasRd := io.store_uops(w).HasRd
      banks(b)(row).HasRs1 := io.store_uops(w).HasRs1
      banks(b)(row).HasRs2 := io.store_uops(w).HasRs2
      banks(b)(row).LDSpecExec := io.store_uops(w).LDSpecExec
      banks(b)(row).branchPredInfo := io.store_uops(w).branchPredInfo
      banks(b)(row).funct3 := io.store_uops(w).funct3
      banks(b)(row).funct7 := io.store_uops(w).funct7
      banks(b)(row).imm := io.store_uops(w).imm
      banks(b)(row).old_pd := io.store_uops(w).old_pd
      banks(b)(row).opcode := io.store_uops(w).opcode
      banks(b)(row).pc := io.store_uops(w).pc
      banks(b)(row).pd := io.store_uops(w).pd
      banks(b)(row).ps1 := io.store_uops(w).ps1
      banks(b)(row).ps2 := io.store_uops(w).ps2
      banks(b)(row).rd := io.store_uops(w).rd
      banks(b)(row).rob_id := io.store_uops(w).rob_id
      banks(b)(row).rs1 := io.store_uops(w).rs1
      banks(b)(row).rs2 := io.store_uops(w).rs2

      // ready signals
      banks(b)(row).rdy1 := !io.busyTableVec(io.store_uops(w).ps1) || aluWakeup_ps1(idx)
      banks(b)(row).rdy2 := !io.busyTableVec(io.store_uops(w).ps2) || aluWakeup_ps2(idx)

      // at ROB head
      val atRobHead = Wire(Bool())
      atRobHead := VecInit(io.robEnable.map(r => r.valid && r.robLoc === banks(b)(row).robLoc)).asUInt.orR
      banks(b)(row).atRobHead := atRobHead

      banks(b)(row).valid := true.B
    }
  }

  // --- Update existing entries ---
  for (b <- 0 until p(STU_NUM)) {
    for (i <- 0 until perBankSize) {
      // Skip newly enqueued entries
      when(!banks(b)(i).valid || banks(b)(i).rdy1 && banks(b)(i).rdy2) {
        banks(b)(i).rdy1 := banks(b)(i).rdy1 || !io.busyTableVec(banks(b)(i).ps1) || aluWakeup_ps1((i * p(STU_NUM) + b).U)
        banks(b)(i).rdy2 := banks(b)(i).rdy2 || !io.busyTableVec(banks(b)(i).ps2) || aluWakeup_ps2((i * p(STU_NUM) + b).U)
        val atRobHead = VecInit(io.robEnable.map(r => r.valid && r.robLoc === banks(b)(i).robLoc)).asUInt.orR
        banks(b)(i).atRobHead := banks(b)(i).atRobHead || atRobHead
      }
    }
  }
}