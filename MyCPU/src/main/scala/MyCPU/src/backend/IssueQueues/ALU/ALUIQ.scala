package MyCPU.backend

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

// alu wakeup at regread stage
// issue enq prepare
// enq + select
class ALUIQ(implicit p: Parameters) extends Module {
  val totalSize = p(ALUIQ_SIZE)
  val perBankSize = totalSize / p(ALU_NUM)
  val ptrWidth = log2Ceil(totalSize)
  val bankBits = log2Ceil(p(ALU_NUM))

  val freeIdBuffer = Module(new ALUIQFreeIdBuffer)
  val wakeUpPortNum = WakeUpQueuePortNum(p)

  val io = IO(new Bundle {
    val flush = Input(Bool())
    val stalls = Input(Vec(p(ALU_NUM), Bool()))
    val alu_uops = Input(Vec(p(FETCH_WIDTH), new uOp))
    val issue0_stall = Output(Bool())
    val wakeup_signals = Input(Vec(wakeUpPortNum, new WakeUpQueueEntry))
    val busytableVec = Input(UInt((1 << p(PREG_WIDTH)).W))
    val aluiq_o = Output(Vec(p(ALU_NUM), new ALUIQEntry))
  })

  // -------------------------
  // Enqueue prepare stage
  // -------------------------
  val allocReqs = VecInit(io.alu_uops.map(_.valid))
  val allocResps = freeIdBuffer.io.allocResp
  freeIdBuffer.io.allocReq := allocReqs
  freeIdBuffer.io.flush := io.flush
  freeIdBuffer.io.stalls := io.stalls

  // Skid buffers for each bank
  class BufferItem extends Bundle with HasValid {
    val uOp = new uOp
    val loc = UInt(ptrWidth.W)
    val valid = Bool()
  }

  val skidBuffers = Seq.fill(p(ALU_NUM))(
    Module(new MultiPortQueueNW1R(p(FETCH_WIDTH) * 2, p(FETCH_WIDTH) / p(ALU_NUM), "ALUIQSkidBuffer", new BufferItem))
  )

  // -------------------------
  // Entry RAM and age matrix
  // -------------------------
  val entryRam = RegInit(VecInit(Seq.fill(p(ALU_NUM))(
    VecInit(Seq.fill(perBankSize)(0.U.asTypeOf(new ALUIQEntry)))
  )))

  // ageMats compressed: each row is a bit vector of "older than" entries
  val ageMats = RegInit(VecInit(Seq.fill(p(ALU_NUM))(
    VecInit(Seq.fill(perBankSize)(0.U(perBankSize.W)))
  )))

  // -------------------------
  // Enqueue logic
  // -------------------------
  val enqUops = RegInit(VecInit(
    Seq.fill(p(ALU_NUM))(0.U.asTypeOf(new uOp))
  ))

  val enqLocs = RegInit(VecInit(
    Seq.fill(p(ALU_NUM))(0.U(ptrWidth.W))
  ))

  for (b <- 0 until p(ALU_NUM)) {
    // Determine hits for this bank
    val bankHits = VecInit((0 until p(FETCH_WIDTH)).map { i =>
      (allocResps(i)(bankBits-1,0) === b.U) && io.alu_uops(i).valid
    })

    val bankHitIndex = PriorityEncoder(bankHits)
    val hasBankHit = bankHits.asUInt.orR

    val useCurrent = Wire(Bool())
    useCurrent := hasBankHit && !skidBuffers(b).io.deqReady

    // Enqueue to skid buffer
    for (i <- 0 until p(FETCH_WIDTH) / p(ALU_NUM)) {
      val bufferItem = WireInit(0.U.asTypeOf(new BufferItem))
      val hitMask = VecInit((0 until p(FETCH_WIDTH)).map { j =>
        (bankHits(j)) && ((i.U) === 0.U)  // simple mapping for 2-depth skid buffer
      })
      val hasHit = hitMask.asUInt.orR
      bufferItem.uOp := io.alu_uops(bankHitIndex)
      bufferItem.loc := allocResps(bankHitIndex)
      bufferItem.valid := hasHit
      skidBuffers(b).io.enq(i) := bufferItem
      skidBuffers(b).io.enqValid(i) := bufferItem.valid
    }

    skidBuffers(b).io.deqValid := true.B
    skidBuffers(b).io.flush := io.flush

    enqUops(b) := Mux(useCurrent, io.alu_uops(bankHitIndex),
                      Mux(skidBuffers(b).io.deqReady, skidBuffers(b).io.deq.uOp,
                          0.U.asTypeOf(new uOp)))
    enqLocs(b) := Mux(useCurrent, allocResps(bankHitIndex),
                       Mux(skidBuffers(b).io.deqReady, skidBuffers(b).io.deq.loc, 0.U))
  }

  io.issue0_stall := !freeIdBuffer.io.deqReady

  // -------------------------
  // Issue stage
  // -------------------------
  for (b <- 0 until p(ALU_NUM)) {
    for (i <- 0 until perBankSize) {
      when(i.U === enqLocs(b)(ptrWidth-1, bankBits) && enqUops(b).valid) {
        // Wakeup logic
        val wakeUp1 = VecInit((0 until wakeUpPortNum).map(j =>
          io.wakeup_signals(j).HasRd && enqUops(b).HasRs1 && io.wakeup_signals(j).pd === enqUops(b).ps1
        )).asUInt.orR
        val wakeUp2 = VecInit((0 until wakeUpPortNum).map(j =>
          io.wakeup_signals(j).HasRd && enqUops(b).HasRs2 && io.wakeup_signals(j).pd === enqUops(b).ps2
        )).asUInt.orR

        val iq_entry = WireInit(0.U.asTypeOf(new ALUIQEntry))
        iq_entry.HasRd := enqUops(b).HasRd
        iq_entry.branchPredInfo := enqUops(b).branchPredInfo
        iq_entry.funct3 := enqUops(b).funct3
        iq_entry.funct7 := enqUops(b).funct7
        iq_entry.imm := enqUops(b).imm
        iq_entry.old_pd := enqUops(b).old_pd
        iq_entry.opcode := enqUops(b).opcode
        iq_entry.pc := enqUops(b).pc
        iq_entry.pd := enqUops(b).pd
        iq_entry.HasRs1 := enqUops(b).HasRs1
        iq_entry.HasRs2 := enqUops(b).HasRs2
        iq_entry.ps1 := enqUops(b).ps1
        iq_entry.ps2 := enqUops(b).ps2
        iq_entry.rd := enqUops(b).rd
        iq_entry.rdy1 := !io.busytableVec(enqUops(b).ps1) || wakeUp1
        iq_entry.rdy2 := !io.busytableVec(enqUops(b).ps2) || wakeUp2
        iq_entry.rs1 := enqUops(b).rs1
        iq_entry.rs2 := enqUops(b).rs2
        iq_entry.valid := enqUops(b).valid
        
        entryRam(b)(i) := iq_entry
      }.otherwise {
        val wakeUp1 = VecInit((0 until wakeUpPortNum).map(j =>
          io.wakeup_signals(j).HasRd && entryRam(b)(i).HasRs1 && io.wakeup_signals(j).pd === entryRam(b)(i).ps1
        )).asUInt.orR
        val wakeUp2 = VecInit((0 until wakeUpPortNum).map(j =>
          io.wakeup_signals(j).HasRd && entryRam(b)(i).HasRs2 && io.wakeup_signals(j).pd === entryRam(b)(i).ps2
        )).asUInt.orR

        entryRam(b)(i).rdy1 := entryRam(b)(i).rdy1 || wakeUp1 || !io.busytableVec(entryRam(b)(i).ps1)
        entryRam(b)(i).rdy2 := entryRam(b)(i).rdy2 || wakeUp2 || !io.busytableVec(entryRam(b)(i).ps2)
      }
    }

    // -------------------------
    // Select logic using ageMats bit vector
    // -------------------------
    val canIssueVec = Wire(Vec(perBankSize, Bool()))
    for (i <- 0 until perBankSize) {
      val ready = !(entryRam(b)(i).HasRs1 && !entryRam(b)(i).rdy1 ||
                    entryRam(b)(i).HasRs2 && !entryRam(b)(i).rdy2)
      val olderReady = (ageMats(b)(i) & VecInit(entryRam(b).map(_.valid)).asUInt).orR
      canIssueVec(i) := ready && !olderReady
    }

    val issueIdx = PriorityEncoder(canIssueVec)
    // when stall is high, not issue
    val issueEnable = canIssueVec.asUInt.orR && !io.stalls(b)
    io.aluiq_o(b) := Mux(issueEnable, entryRam(b)(issueIdx), 0.U.asTypeOf(new ALUIQEntry))

    // -------------------------
    // Write back issued ID to freeIdBuffer
    // -------------------------
    freeIdBuffer.io.writeReq(b).valid := issueEnable
    freeIdBuffer.io.writeReq(b).id := issueIdx

    // -------------------------
    // Update age matrix
    // -------------------------
    for (i <- 0 until perBankSize) {
      val update = (i.U === enqLocs(b)(ptrWidth-1, bankBits) && enqUops(b).valid) || (canIssueVec(i) && !io.stalls(b))
      when(update) {
        ageMats(b)(i) := 0.U
        for (j <- 0 until perBankSize) {
          if (i != j) {
            ageMats(b)(j) := ageMats(b)(j) | (entryRam(b)(j).valid << i.U)
          }
        }
      }
    }
  }
}