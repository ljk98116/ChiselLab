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

      val trace = (0 until 4096).map { i =>
        val base = 0x80000000L
        val pc = base + (i % 512) * 4

        val funcA = base + 0x100
        val funcB = base + 0x200
        val funcC = base + 0x300

        // ====== 主入口 ======
        if (pc == base) {
          Instr(pc, isCall = true, isBranch = true, taken = true, target = funcA)
        }

        // ====== 函数 A ======
        else if (pc >= funcA && pc < funcA + 0x40) {
          val idx = ((pc - funcA) / 4).toInt

          if (idx == 0) {
            // call B
            Instr(pc, isCall = true, isBranch = true, taken = true, target = funcB)
          }
          else if (idx % 4 == 0) {
            // pattern: TTNT
            Instr(pc, isBranch = true, taken = true, target = pc + 8)
          }
          else if (idx % 4 == 1) {
            Instr(pc, isBranch = true, taken = true, target = pc + 8)
          }
          else if (idx % 4 == 2) {
            Instr(pc, isBranch = true, taken = false)
          }
          else {
            Instr(pc) // 顺序执行
          }
        }
        else if (pc == funcA + 0x40) {
          // return
          Instr(pc, isReturn = true, isBranch = true, taken = true)
        }

        // ====== 函数 B ======
        else if (pc >= funcB && pc < funcB + 0x40) {
          val idx = ((pc - funcB) / 4).toInt

          if (idx == 0) {
            // call C
            Instr(pc, isCall = true, isBranch = true, taken = true, target = funcC)
          }
          else if (idx % 3 == 0) {
            // pattern: TNN
            Instr(pc, isBranch = true, taken = true, target = pc + 8)
          }
          else {
            Instr(pc, isBranch = true, taken = false)
          }
        }
        else if (pc == funcB + 0x40) {
          Instr(pc, isReturn = true, isBranch = true, taken = true)
        }

        // ====== 函数 C ======
        else if (pc >= funcC && pc < funcC + 0x40) {
          val idx = ((pc - funcC) / 4).toInt

          if (idx % 2 == 0) {
            // pattern: TN
            Instr(pc, isBranch = true, taken = true, target = pc + 8)
          }
          else {
            Instr(pc, isBranch = true, taken = false)
          }
        }
        else if (pc == funcC + 0x40) {
          Instr(pc, isReturn = true, isBranch = true, taken = true)
        }

        // ====== 干扰区（alias + noise）======
        else if ((i % 16) == 0) {
          // 跳回入口（制造全局历史扰动）
          Instr(pc, isBranch = true, taken = true, target = base)
        }
        else if ((i % 7) == 0) {
          Instr(pc, isBranch = true, taken = false)
        }
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
        var ghr: Long = 0
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

          if(inst.isBranch) {
            ghr = ghr >> 1L
            if(inst.taken) {
              ghr |= (1L << 7)
            }
          }
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
        def PC(): Long = pc
        def GHR(): Long = ghr & 0xFF
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
      
      var if0_pcVec = IndexedSeq.fill(fetchWidth)(0.U(p(ADDR_WIDTH).W))
      var if0_pcValid = IndexedSeq.fill(fetchWidth)(false.B)

      while(cycle < 9551) {
        // commit redirect
        var commit_redirect = false
        // all reset to zero
        dut.io.commit_redirect.flush.poke(false.B)
        dut.io.commit_redirect.flush_pc.poke(0.U)
        dut.io.commit_redirect.isCall.poke(false.B)
        dut.io.commit_redirect.isReturn.poke(false.B)
        dut.io.commit_redirect.pc.poke(0.U)
        dut.io.commit_redirect.rasTopPtr.poke(0.U)

        dut.io.icache_stall_req.poke(false.B)
        dut.io.stall_from_backpipe.poke(false.B)
        dut.io.icache_refill_valid.poke(false.B)
        
        // icache always hit
        dut.io.if2_icache_hit.poke(true.B)

        // if2 predecode result drive
        for(i <- 0 until fetchWidth) {
          if(dut.io.if2_valid_vec(i).peek().litToBoolean) {
            val if2_predecodeRes = predecoder(dut.io.if2_pc_vec(i).peek().litValue.longValue)
            dut.io.if2_predecode(i).isCall.poke(if2_predecodeRes.isCall().B)
            dut.io.if2_predecode(i).isReturn.poke(if2_predecodeRes.isReturn().B)
            dut.io.if2_predecode(i).isDirectJump.poke(if2_predecodeRes.isCall().B)
            dut.io.if2_predecode(i).isIndirectJump.poke(
              if2_predecodeRes.isIndirectJump().B
            )
            dut.io.if2_predecode(i).isConditionalBranch.poke(
              if2_predecodeRes.isConditionalBranch().B
            )
            dut.io.if2_predecode(i).isUnconditionalBranch.poke(
              if2_predecodeRes.isUnconditionalBranch().B
            )
          }
          else {
            dut.io.if2_predecode(i).isCall.poke(false.B)
            dut.io.if2_predecode(i).isReturn.poke(false.B)
            dut.io.if2_predecode(i).isDirectJump.poke(false.B)
            dut.io.if2_predecode(i).isIndirectJump.poke(
              false.B
            )
            dut.io.if2_predecode(i).isConditionalBranch.poke(
              false.B
            )
            dut.io.if2_predecode(i).isUnconditionalBranch.poke(
              false.B
            )            
          }
        }

        // ITLB always hit VA=PA
        for(i <- 0 until fetchWidth) {
          dut.io.if1_itlb_resp(i).hit.poke(true.B)
          dut.io.if1_itlb_resp(i).paddr.bits.poke(if0_pcVec(i).litValue.longValue.U)
          dut.io.if1_itlb_resp(i).valid.poke(if0_pcValid(i).litToBoolean.B)
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
          if(if5_branchPreds(i).valid.litToBoolean) {
            val meta = predecoder(goldencpu.PC())
            if(meta.isCall() || meta.isReturn() || meta.isConditionalBranch()) btbAccessCount += 1
          }
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

            val misPred = (realTaken != if5_taken || realTarget != if5_predTarget)

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
              val branchmisPred = (realTaken != if5_taken || realTarget != if5_predTarget)
              branchTotal += 1
              if(!branchmisPred) correctTotal += 1
            }

            dut.io.wb1_predictor_update(i).valid.poke(realIsBranch.B)
            dut.io.wb1_predictor_update(i).counter.poke(if5_counter.U)
            dut.io.wb1_predictor_update(i).ghist.poke(if5_ghist.U)
            dut.io.wb1_predictor_update(i).lhist.poke(if5_lhist.U)
            dut.io.wb1_predictor_update(i).misPred.poke(misPred.B)
            dut.io.wb1_predictor_update(i).pc.poke(if5_pc.U)
            dut.io.wb1_predictor_update(i).taken.poke(realTaken.B)

            dut.io.wb1_l1_btb_update(i).valid.poke(realIsBranch.B)
            dut.io.wb1_l1_btb_update(i).pc.poke(if5_pc.U)
            dut.io.wb1_l1_btb_update(i).isCall.poke(realIsCall.B)
            dut.io.wb1_l1_btb_update(i).isReturn.poke(realIsReturn.B)
            dut.io.wb1_l1_btb_update(i).target.poke(((realTarget - realPC) & 0xFF).U)

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
            println(s"cycle[$cycle]: Branch Pred Acc: ${acc}%, branchTotal:$branchTotal")
            println(s"cycle[$cycle]: l0 btb hit rate: ${l0Rate}%, btbAccessCount:$btbAccessCount")
            println(s"cycle[$cycle]: l1 btb hit rate: ${l1Rate}%, btbAccessCount:$btbAccessCount")
            println(s"cycle[$cycle]: ras correct rate: ${rasRate}%, rasTotal: $rasTotal")
          }
          else println(s"cycle[${cycle}]: no branch found")
          println("")
        }

        dut.io.refGHR.poke(goldencpu.GHR().U)

        // if1 itlb
        dut.clock.step(1)
        
        if0_pcVec = dut.io.if0_itlb_req.peek().map(_.addr)
        if0_pcValid = dut.io.if0_itlb_req.peek().map(_.valid)

        cycle += 1
      }
    }
  }
}


