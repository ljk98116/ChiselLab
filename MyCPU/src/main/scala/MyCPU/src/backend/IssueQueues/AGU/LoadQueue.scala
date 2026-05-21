package MyCPU.backend

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

// to do, when store write done, need to wakeup related load
class LoadQueue(implicit p: Parameters) extends Module {
  val totalSize = p(LDUIQ_SIZE)
  val perBankSize = totalSize / p(LDU_NUM)
  val ptrWidth = log2Ceil(totalSize)
  val bankBits = log2Ceil(p(LDU_NUM))

  val freeIdBuffer = Module(new LDUIQFreeIdBuffer)

  val io = IO(new Bundle {
    val flush = Input(Bool())
    val stall_from_backpipe = Input(Bool())
    val ldu_uops = Input(Vec(p(FETCH_WIDTH), new uOp))
    val issue0_stall = Output(Bool())
    val busytableVec = Input(UInt((1 << p(PREG_WIDTH)).W))
    val aluSelectRes = Input(Vec(p(ALU_NUM), new ALUIQEntry))
    val ldRobEnable = Input(Vec(p(FETCH_WIDTH), new LoadQueueEnable))
    val stRobEnable = Input(Vec(p(FETCH_WIDTH), new StoreQueueEnable))
    val lduiq_o = Output(Vec(p(LDU_NUM), new LDUIQEntry))
  })

  // -------------------------
  // Enqueue prepare stage
  // -------------------------
  val allocReqs = VecInit(io.ldu_uops.map(_.valid))
  val allocResps = freeIdBuffer.io.allocResp
  freeIdBuffer.io.allocReq := allocReqs
  freeIdBuffer.io.flush := io.flush
  freeIdBuffer.io.stall_from_backpipe := io.stall_from_backpipe

  class BufferItem extends Bundle with HasValid {
    val uOp = new uOp
    val loc = UInt(ptrWidth.W)
    val valid = Bool()
  }

  val skidBuffers = Seq.fill(p(LDU_NUM))(
    Module(new MultiPortQueueNW1R(p(FETCH_WIDTH), p(FETCH_WIDTH) / p(LDU_NUM), new BufferItem))
  )

  val enqUops = RegInit(VecInit(
    Seq.fill(p(LDU_NUM))(0.U.asTypeOf(new uOp))
  ))
  
  val enqLocs = RegInit(VecInit(
    Seq.fill(p(LDU_NUM))(0.U(ptrWidth.W))
  ))

  // -------------------------
  // Entry RAM and age matrix
  // -------------------------
  val entryRam = RegInit(VecInit(Seq.fill(p(LDU_NUM))(
    VecInit(Seq.fill(perBankSize)(0.U.asTypeOf(new LDUIQEntry)))
  )))

  // ageMats compressed: each row is a bit vector of "older than" entries
  val ageMats = RegInit(VecInit(Seq.fill(p(LDU_NUM))(
    VecInit(Seq.fill(perBankSize)(0.U(perBankSize.W)))
  )))

  // -------------------------
  // Enqueue logic
  // -------------------------
  for (b <- 0 until p(LDU_NUM)) {
    val bankHits = VecInit((0 until p(FETCH_WIDTH)).map { i =>
      (allocResps(i)(bankBits-1,0) === b.U) && io.ldu_uops(i).valid
    })
    val bankHitIndex = PriorityEncoder(bankHits)
    val hasBankHit = bankHits.asUInt.orR
    val useCurrent = Wire(Bool())
    useCurrent := hasBankHit && !skidBuffers(b).io.deqReady

    for (i <- 0 until p(FETCH_WIDTH) / p(LDU_NUM)) {
      val bufferItem = WireInit(0.U.asTypeOf(new BufferItem))
      bufferItem.uOp := io.ldu_uops(bankHitIndex)
      bufferItem.loc := allocResps(bankHitIndex)
      bufferItem.valid := hasBankHit
      skidBuffers(b).io.enq(i) := bufferItem
      skidBuffers(b).io.enqValid(i) := bufferItem.valid
    }

    skidBuffers(b).io.deqValid := true.B
    skidBuffers(b).io.flush := io.flush

    enqUops(b) := Mux(useCurrent, io.ldu_uops(bankHitIndex),
                       Mux(skidBuffers(b).io.deqReady, skidBuffers(b).io.deq.uOp, 0.U.asTypeOf(new uOp)))
    enqLocs(b) := Mux(useCurrent, allocResps(bankHitIndex),
                        Mux(skidBuffers(b).io.deqReady, skidBuffers(b).io.deq.loc, 0.U))
  }

  io.issue0_stall := !freeIdBuffer.io.deqReady

  // -------------------------
  // Issue stage
  // -------------------------
  for (b <- 0 until p(LDU_NUM)) {
    for (i <- 0 until perBankSize) {
      // load reached rob top 
      val canExec = VecInit((0 until p(FETCH_WIDTH)).map(j =>
        io.ldRobEnable(j).valid && (io.ldRobEnable(j).robId === entryRam(b)(i).rob_id) && entryRam(b)(i).valid
      )).asUInt.orR

      val canSpecExec = VecInit((0 until p(SLOT_NUM)).map(j => 
        VecInit((0 until p(FETCH_WIDTH)).map(k => 
          io.stRobEnable(k).valid && (io.stRobEnable(k).robId === entryRam(b)(i).blockStIds(j)) && entryRam(b)(i).canSpecExec(j)
        )).asUInt.orR
      ))

      val aluWakeUp1 = VecInit((0 until p(ALU_NUM)).map(j =>
        io.aluSelectRes(j).HasRd && enqUops(b).HasRs1 && io.aluSelectRes(j).pd === enqUops(b).ps1
      )).asUInt.orR

      val aluWakeUp2 = VecInit((0 until p(ALU_NUM)).map(j =>
        io.aluSelectRes(j).HasRd && enqUops(b).HasRs2 && io.aluSelectRes(j).pd === enqUops(b).ps2
      )).asUInt.orR

      when(i.U === enqLocs(b)(ptrWidth-1, bankBits) && enqUops(b).valid) {
        val iq_entry = WireInit(0.U.asTypeOf(new LDUIQEntry))
        iq_entry := enqUops(b).asTypeOf(new LDUIQEntry)  // 直接类型转换减少重复赋值
        iq_entry.rdy1 := !io.busytableVec(enqUops(b).ps1) || aluWakeUp1
        iq_entry.rdy2 := !io.busytableVec(enqUops(b).ps2) || aluWakeUp2
        iq_entry.LDSpecExec(0) := enqUops(b).LDSpecExec(0) || canSpecExec(0) || canExec
        iq_entry.LDSpecExec(1) := enqUops(b).LDSpecExec(1) || canSpecExec(1) || canExec
        entryRam(b)(enqLocs(b)(ptrWidth-1, bankBits)) := iq_entry
      }.otherwise {
        entryRam(b)(i).rdy1 := entryRam(b)(i).rdy1 || aluWakeUp1 || !io.busytableVec(entryRam(b)(i).ps1)
        entryRam(b)(i).rdy2 := entryRam(b)(i).rdy2 || aluWakeUp2 || !io.busytableVec(entryRam(b)(i).ps2)
        entryRam(b)(i).LDSpecExec(0) := entryRam(b)(i).LDSpecExec(0) || canSpecExec(0) || canExec
        entryRam(b)(i).LDSpecExec(1) := entryRam(b)(i).LDSpecExec(1) || canSpecExec(1) || canExec
      }
    }

    // -------------------------
    // Select logic using ageMats bit vector
    // -------------------------
    val canIssueVec = Wire(Vec(perBankSize, Bool()))
    for (i <- 0 until perBankSize) {
      val ready = entryRam(b)(i).valid && 
                  !(entryRam(b)(i).HasRs1 && !entryRam(b)(i).rdy1 || 
                    entryRam(b)(i).HasRs2 && !entryRam(b)(i).rdy2) && 
                  entryRam(b)(i).LDSpecExec.asUInt.andR
      val olderReady = (ageMats(b)(i) & VecInit(entryRam(b).map(_.valid)).asUInt).orR
      canIssueVec(i) := ready && !olderReady
    }

    val issueIdx = PriorityEncoder(canIssueVec)
    val issueEnable = canIssueVec.asUInt.orR
    io.lduiq_o(b) := Mux(issueEnable, entryRam(b)(issueIdx), 0.U.asTypeOf(new LDUIQEntry))

    // -------------------------
    // Write back issued ID to freeIdBuffer
    // -------------------------
    freeIdBuffer.io.writeReq(b).valid := issueEnable
    freeIdBuffer.io.writeReq(b).id := issueIdx

    // -------------------------
    // Update age matrix
    // -------------------------
    for (i <- 0 until perBankSize) {
      val update = (i.U === enqLocs(b)(ptrWidth-1, bankBits) && enqUops(b).valid) || canIssueVec(i)
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