package MyCPU.test

import chisel3._
import chiseltest._
import org.scalatest.freespec.AnyFreeSpec
import scala.collection.mutable.Queue

import org.chipsalliance.cde.config._
import MyCPU.frontend._
import MyCPU.config._
import MyCPU.io._
import chisel3.util.PopCount

case class Instr(
  pc: Long,
  isCall: Boolean = false,
  isReturn: Boolean = false,
  isBranch:Boolean = false,
  taken: Boolean = false,
  target: Long = 0
)

class Meta(
  isCall: Boolean = false,
  isReturn: Boolean = false,
  isDirectJump: Boolean = false,
  isIndirectJump: Boolean = false,
  isConditionalBranch: Boolean = false,
  isUnconditionalBranch: Boolean = false
) {
  def isCall(): Boolean = isCall
  def isReturn(): Boolean = isReturn
  def isDirectJump(): Boolean = isDirectJump
  def isIndirectJump(): Boolean = isIndirectJump
  def isConditionalBranch(): Boolean = isConditionalBranch
  def isUnconditionalBranch(): Boolean = isUnconditionalBranch
}

class FrontEndBranchPredTest extends AnyFreeSpec with ChiselScalatestTester {

  implicit val p: Parameters =
    new BTBConfig ++ new BaseConfig ++
    new GSharePredictorConfig ++ new L1CacheConfig ++
    new UseSv32Config ++ new UseCustomLibraryConfig

  "Frontend Branch Prediction (Correct Redirect Model)" in {

    test(new FrontEnd).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

      val fetchWidth = p(FETCH_WIDTH)

      val base     = 0x80000000L
      val funcA    = base + 0x400
      val funcB    = base + 0x800

      val trace = (0 until 2048).map { i =>
        val pc = base + i * 4

        // =========================
        // main loop
        // =========================
        if (pc < funcA) {

          if (i % 16 == 0) {
            // call funcA
            Instr(pc,
              isCall = true,
              isReturn = false,
              isBranch = true,
              taken = true,
              target = funcA
            )
          }
          else if (i % 4 == 0) {
            // conditional branch（pattern）
            val taken = ((i / 4) % 2 == 0)   // 0101 pattern（GShare友好）
            Instr(pc,
              isBranch = true,
              taken = taken,
              target = pc + 16
            )
          }
          else {
            Instr(pc) // normal
          }
        }

        // =========================
        // funcA
        // =========================
        else if (pc >= funcA && pc < funcA + 0x40) {

          if (pc == funcA) {
            // entry cond branch
            Instr(pc,
              isBranch = true,
              taken = true,
              target = pc + 8
            )
          }
          else if (pc == funcA + 0x8) {
            // call funcB
            Instr(pc,
              isCall = true,
              isReturn = false,
              isBranch = true,
              taken = true,
              target = funcB
            )
          }
          else if (pc == funcA + 0x10) {
            // pattern branch
            val taken = ((i / 8) % 2 == 0)
            Instr(pc,
              isBranch = true,
              taken = taken,
              target = pc + 12
            )
          }
          else if (pc == funcA + 0x3C) {
            // return
            Instr(pc,
              isReturn = true,
              isBranch = true,
              taken = true,
              target = 0
            )
          }
          else {
            Instr(pc)
          }
        }

        // =========================
        // funcB
        // =========================
        else if (pc >= funcB && pc < funcB + 0x20) {

          if (pc == funcB) {
            val taken = ((i / 2) % 2 == 0)
            Instr(pc,
              isBranch = true,
              taken = taken,
              target = pc + 8
            )
          }
          else if (pc == funcB + 0x1C) {
            // return
            Instr(pc,
              isReturn = true,
              isBranch = true,
              taken = true,
              target = 0
            )
          }
          else {
            Instr(pc)
          }
        }

        // =========================
        // fallback
        // =========================
        else {
          Instr(pc)
        }
      }

      def getInstr(pc: Long): Instr =
        trace(((pc - 0x80000000) / 4 % trace.length).toInt)

      def predecoder(pc: Long): Meta = {
        val inst = getInstr(pc)
        var res = new Meta(inst.isCall, inst.isReturn, false, false, !(inst.isCall || inst.isReturn), inst.isCall || inst.isReturn)
        return res
      }

      class GoldenCPU(initPC: Long) {
        var pc: Long = initPC
        var goldenRAS = scala.collection.mutable.Stack[Long]()
        var nextpc = pc + 4L
        def step(): Long = {
          var oldpc = pc
          val inst = getInstr(pc)
          if(inst.isCall) {
            goldenRAS.push(pc + 4L)
            nextpc = inst.target
          }
          else if(inst.isReturn) {
            nextpc = goldenRAS.top
            goldenRAS.pop()
          }
          else if(inst.isBranch && inst.taken) nextpc = inst.target
          else nextpc = pc + 4L
          
          pc = nextpc
          return oldpc
        }

        def target():Long = {
          var target = pc + 4L
          val inst = getInstr(pc)
          if(inst.isCall) {
            target = inst.target
          }
          else if(inst.isReturn) {
            target = goldenRAS.top
          }
          else if(inst.isBranch && inst.taken) target = inst.target

          return target
        }
      }

      var cycle = 0
      var predecodeQueue = new Queue[Array[Meta]]
      var predecodeCounter = 2 // if2 output

      var instQueue = new Queue[(Boolean, Instr)]
      var icacheCounter = 2 // if2 output

      var branchTotal = 0 // cond jumps
      var correctTotal = 0
      var rasTotal = 0 // call/return
      var rasCorrectTotal = 0 

      var goldencpu = new GoldenCPU(0x80000000L)
      var btbAccessCount = 0
      
      while(cycle < 1501) {
        // commit redirect
        var commit_redirect = false
        // all reset to zero
        dut.io.commit_redirect.flush.poke(false.B)
        dut.io.icache_stall_req.poke(false.B)
        dut.io.stall_from_backpipe.poke(false.B)
        dut.io.icache_refill_valid.poke(false.B)

        var if2_predecodeRes = Array.fill(fetchWidth)(new Meta)
        var if2_instQueue = new Queue[(Boolean, Instr)]
        
        // icache always hit
        dut.io.if2_icache_hit.poke(true.B)

        // // wait 2 cycle, then output per cycle
        if(predecodeCounter == 0) {
          if(predecodeQueue.nonEmpty) {
            if2_predecodeRes = predecodeQueue.dequeue()
          }
          else println(s"[PREDECODE INFO]: cycle [$cycle] predecode queue empty")
        }
        else predecodeCounter -= 1

        // wait 2 cycle, then output per cycle
        if(icacheCounter == 0) {
          for(i <- 0 until fetchWidth) {
            if(instQueue.nonEmpty) {
              if2_instQueue.enqueue(instQueue.dequeue())
            }
            else println(s"[ICACHE INFO]: cycle [$cycle] inst queue empty")
          }
        }
        else icacheCounter -= 1

        // if2 predecode result drive
        for(i <- 0 until fetchWidth) {
          dut.io.if2_predecode(i).isCall.poke(if2_predecodeRes(i).isCall().B)
          dut.io.if2_predecode(i).isReturn.poke(if2_predecodeRes(i).isReturn().B)
          dut.io.if2_predecode(i).isDirectJump.poke(if2_predecodeRes(i).isCall().B)
          dut.io.if2_predecode(i).isIndirectJump.poke(
            if2_predecodeRes(i).isIndirectJump().B
          )
          dut.io.if2_predecode(i).isConditionalBranch.poke(
            if2_predecodeRes(i).isConditionalBranch().B
          )
          dut.io.if2_predecode(i).isUnconditionalBranch.poke(
            if2_predecodeRes(i).isUnconditionalBranch().B
          )
        }

        // get predecode result, enq
        val if0_pcVec = dut.io.if0_itlb_req.peek()
        val if0_pcValid = dut.io.if0_itlb_req.peek()

        var predecodeArray = Array.fill(fetchWidth)(new Meta)

        // if2 predecode
        for(i <- 0 until fetchWidth) {
          if(if0_pcValid(i).valid.litToBoolean) {
            predecodeArray(i) = predecoder(if0_pcVec(i).litValue.longValue)
          }
        }
        predecodeQueue.enqueue(predecodeArray)

        // if2 icache
        for(i <- 0 until fetchWidth) {
          instQueue.enqueue((if0_pcValid(i).valid.litToBoolean, getInstr(if0_pcVec(i).litValue.longValue)))
        }

        // ITLB always hit VA=PA
        for(i <- 0 until fetchWidth) {
          dut.io.if1_itlb_resp(i).hit.poke(true.B)
          dut.io.if1_itlb_resp(i).paddr.bits.poke(if0_pcVec(i).litValue.longValue.U)
          dut.io.if1_itlb_resp(i).valid.poke(if0_pcValid(i).valid.litToBoolean.B)
        }

        // inst set to 0, because no decode in frontend 
        for (i <- 0 until p(L1CacheLineLength)) {
          dut.io.if2_icacheline(i).poke((1000 + i).U)
        }

        val if5_valids = dut.io.if5_instValid.peek()
        val if5_pcs = dut.io.if5_pc_vec.peek()

        // get if5 output, get branch result and judge redirect
        val if5_branchPreds = dut.io.if5_br_pred_info_vec.peek()

        for(i <- 0 until fetchWidth) {
          dut.io.wb1_predictor_update(i).valid.poke(false.B)
          dut.io.wb1_predictor_update(i).counter.poke(0.U)
          dut.io.wb1_predictor_update(i).ghist.poke(0.U)
          dut.io.wb1_predictor_update(i).lhist.poke(0.U)
          dut.io.wb1_predictor_update(i).misPred.poke(0.B)
          dut.io.wb1_predictor_update(i).pc.poke(0.U)
          dut.io.wb1_predictor_update(i).taken.poke(false.B)

          dut.io.wb1_l1_btb_update(i).valid.poke(false.B)
          dut.io.wb1_l1_btb_update(i).pc.poke(0.U)
          dut.io.wb1_l1_btb_update(i).isCall.poke(false.B)
          dut.io.wb1_l1_btb_update(i).isReturn.poke(false.B)
          dut.io.wb1_l1_btb_update(i).target.poke(0.U) 
        }

        for(i <- 0 until fetchWidth) {
          if(if5_branchPreds(i).valid.litToBoolean && !commit_redirect) {
            val if5_predTarget = if5_branchPreds(i).target.litValue.longValue
            val if5_isCall = if5_branchPreds(i).isCall.litToBoolean
            val if5_isReturn = if5_branchPreds(i).isReturn.litToBoolean
            val if5_taken = if5_branchPreds(i).counter.litValue.longValue >= 2
            val if5_pc = if5_pcs(i).litValue.longValue
            val if5_ghist = if5_branchPreds(i).ghist.litValue.longValue
            val if5_lhist = if5_branchPreds(i).lhist.litValue.longValue
            val if5_rasTopPtr = if5_branchPreds(i).rasTopPtr.litValue.longValue
            val if5_counter = if5_branchPreds(i).counter.litValue.longValue

            val realTarget = goldencpu.target()
            val realPC = goldencpu.step()

            val instr = getInstr(realPC)
            val realIsCall = instr.isCall
            val realIsReturn = instr.isReturn
            val realTaken = instr.taken
            val realIsBranch = instr.isBranch

            val misPred = realTaken != if5_taken || realTarget != if5_predTarget

            if(realPC != if5_pc) {
              println(s"[PC ERROR]: cycle[${cycle}] if5 pc 0x${if5_pc.toHexString} golden pc 0x${realPC.toHexString}")
            }
            // else println(s"[PC INFO]: cycle[${cycle}] if5 pc 0x${if5_pc.toHexString} golden pc 0x${realPC.toHexString}")

            if(realIsReturn) {
              rasTotal += 1
              if(realIsReturn && !misPred) {
                rasCorrectTotal += 1
              }
            }

            // branch but not call/return
            if(realIsBranch && !realIsCall && !realIsReturn) {
              branchTotal += 1
              if(!misPred) correctTotal += 1
            }

            if(realIsBranch && !realIsCall && !realIsReturn) {
              btbAccessCount += 1
            }

            if(misPred) {
              // flush
              icacheCounter = 2
              predecodeCounter = 2
              instQueue.clear()
              predecodeQueue.clear()

              // redirect only once per cycle
              if(!commit_redirect) {
                dut.io.commit_redirect.flush.poke(true.B)
                dut.io.commit_redirect.flush_pc.poke(realTarget.U)
                dut.io.commit_redirect.isCall.poke(realIsCall.B)
                dut.io.commit_redirect.isReturn.poke(realIsReturn.B)
                dut.io.commit_redirect.pc.poke(realPC.U)
                dut.io.commit_redirect.rasTopPtr.poke(if5_rasTopPtr.U)
                commit_redirect = true
              }

              dut.io.wb1_predictor_update(i).valid.poke(true.B)
              dut.io.wb1_predictor_update(i).counter.poke(if5_counter.U)
              dut.io.wb1_predictor_update(i).ghist.poke(if5_ghist.U)
              dut.io.wb1_predictor_update(i).lhist.poke(if5_lhist.U)
              dut.io.wb1_predictor_update(i).misPred.poke(true.B)
              dut.io.wb1_predictor_update(i).pc.poke(if5_pc.U)
              dut.io.wb1_predictor_update(i).taken.poke(realTaken.B)

              dut.io.wb1_l1_btb_update(i).valid.poke(true.B)
              dut.io.wb1_l1_btb_update(i).pc.poke(if5_pc.U)
              dut.io.wb1_l1_btb_update(i).isCall.poke(realIsCall.B)
              dut.io.wb1_l1_btb_update(i).isReturn.poke(realIsReturn.B)
              dut.io.wb1_l1_btb_update(i).target.poke(realTarget.U)
            }
          }
        }

        // stat
        if(cycle % 50 == 0 && cycle > 0) {
          if(branchTotal > 0 && rasTotal > 0) {
            val l0btbhitCount = dut.io.l0btbhitCount.peek().litValue.longValue
            val l1btbhitCount = dut.io.l1btbhitCount.peek().litValue.longValue
            val acc = correctTotal.toDouble / branchTotal * 100.0
            val l0Rate = l0btbhitCount.toDouble / btbAccessCount * 100
            val l1Rate = l1btbhitCount.toDouble / btbAccessCount * 100
            val rasRate = rasCorrectTotal.toDouble / rasTotal * 100
            println(s"Branch Pred Acc: ${acc}%")
            println(s"l0 btb hit rate: ${l0Rate}%, btbAccessCount:$btbAccessCount")
            println(s"l1 btb hit rate: ${l1Rate}%, btbAccessCount:$btbAccessCount")
            println(s"ras correct rate: ${rasRate}%, rasTotal: $rasTotal")
          }
          else println(s"cycle[${cycle}]: no branch found")
          println("")
        }
        // if1 itlb
        dut.clock.step(1)
        
        cycle += 1
      }
    }
  }
}


