package MyCPU.test

import chisel3._
import chiseltest._
import org.scalatest.freespec.AnyFreeSpec
import scala.collection.mutable.Queue

import org.chipsalliance.cde.config._
import MyCPU.frontend._
import MyCPU.config._
import MyCPU.io._

class FrontEndBranchPredTest extends AnyFreeSpec with ChiselScalatestTester {

  implicit val p: Parameters =
    new BTBConfig ++ new BaseConfig ++
    new GSharePredictorConfig ++ new L1CacheConfig ++
    new UseSv32Config ++ new UseCustomLibraryConfig

    case class Instr(
      pc: Long,
      isCall: Boolean = false,
      isReturn: Boolean = false,
      isBranch:Boolean = false,
      taken: Boolean = false,
      target: Long = 0
    )

  "Frontend Branch Prediction (Correct Redirect Model)" in {

    test(new FrontEnd).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

      val fetchWidth = p(FETCH_WIDTH)

      val base = 0x80000000L

      val trace = (0 until 512).map { i =>
        val pc = base + i * 4

        if (i % 16 == 0) {
          Instr(pc, isCall = true, taken = true, isBranch = true, target = base + 0x1000)
        }
        else if (i % 16 == 8) {
          Instr(pc, isReturn = true, taken = true, isBranch = true, target = 0)
        }
        else {
          val taken = (i % 2 == 0)
          Instr(pc, taken = taken, isBranch = true, target = pc + 16)
        }
      }

      val goldenRAS = scala.collection.mutable.Stack[Long]()

      def getInstr(pc: Long): Instr =
        trace(((pc - 0x80000000) / 4 % trace.length).toInt)

      // =========================
      // pipeline model
      // =========================
      val commitQ = Queue[Seq[(Long, BranchPredInfo)]]()

      val PIPELINE_LAT = 6   // IF0→IF5
      val TRAIN_LAT = 4      // WB1→WB3

      val statQ = Queue[(Boolean, Boolean)]()

      var correct = 0
      var total = 0

      var rasCorrectCnt = 0
      var rasTotalCnt = 0

      var commit_isCall = false
      var commit_isReturn = false

      // =========================
      // init
      // =========================
      dut.io.icache_stall_req.poke(false.B)
      dut.io.stall_from_backpipe.poke(false.B)

      dut.io.commit_redirect.flush.poke(false.B)
      dut.io.commit_redirect.flush_pc.poke(0.U)
      dut.io.commit_redirect.pc.poke(0.U)
      dut.io.commit_redirect.isCall.poke(false.B)
      dut.io.commit_redirect.isReturn.poke(false.B)

      dut.io.icache_refill_valid.poke(false.B)
      dut.io.icache_refill_pa.bits.poke(0.U)

      // =========================
      // 主循环
      // =========================
      for (cycle <- 0 until 1000) {

        // 默认
        dut.io.commit_redirect.flush.poke(false.B)
        dut.io.icache_stall_req.poke(false.B)
        dut.io.stall_from_backpipe.poke(false.B)

        // icache always hit
        dut.io.if2_icache_hit.poke(true.B)
        for (i <- 0 until p(L1CacheLineLength)) {
          dut.io.if2_icacheline(i).poke((1000 + i).U)
        }

        // =========================
        // ⭐ 核心：if2_predecode 注入
        // =========================
        for (i <- 0 until fetchWidth) {
          val pc = dut.io.if5_pc_vec(i).peek().litValue.toLong
          val instr = getInstr(pc)

          val meta = dut.io.if2_predecode(i)

          // 默认清零
          meta.isCall.poke(false.B)
          meta.isReturn.poke(false.B)
          meta.isDirectJump.poke(false.B)
          meta.isIndirectJump.poke(false.B)
          meta.isConditionalBranch.poke(false.B)
          meta.isUnconditionalBranch.poke(false.B)

          meta.isCall.poke(instr.isCall.B)
          meta.isReturn.poke(instr.isReturn.B)

          if(instr.isCall || instr.isReturn) {
            meta.isUnconditionalBranch.poke(true.B)
          }
          else if(instr.taken || !instr.taken) {
            meta.isConditionalBranch.poke(true.B)
          }
        }

        // ITLB 直通
        for (i <- 0 until fetchWidth) {
          dut.io.if1_itlb_resp(i).paddr.bits.poke(
            dut.io.if0_itlb_req(i).addr.peek()
          )
        }

        // =========================
        // IF5 收集
        // =========================
        val bundle = (0 until fetchWidth).flatMap { i =>
          val valid = dut.io.if5_instValid(i).peek().litToBoolean
          if (!valid) None
          else {
            val pc = dut.io.if5_pc_vec(i).peek().litValue.longValue
            val pred = dut.io.if5_br_pred_info_vec(i)
            Some((pc, pred))
          }
        }

        commitQ.enqueue(bundle)

        // =========================
        // WB1 update + redirect
        // =========================
        var redirect = false
        var redirectPC = 0L
        var currentPC = 0L
        if (commitQ.size > PIPELINE_LAT) {

          val b = commitQ.dequeue()

          for (i <- 0 until fetchWidth) {

            if (i < b.length) {

              val (pc, predInfo) = b(i)
              val instr = getInstr(pc)

              if (instr.isBranch) {

                val predTaken =
                  predInfo.counter.peek().litValue.toInt >= 2

                val realTaken = instr.taken

                val misPred = predTaken != realTaken

                // -------- predictor update --------
                val upd = dut.io.wb1_predictor_update(i)

                upd.valid.poke(true.B)
                upd.pc.poke(pc.U)
                upd.taken.poke(realTaken.B)
                upd.misPred.poke(misPred.B)
                upd.counter.poke(predInfo.counter.peek())
                upd.ghist.poke(predInfo.ghist.peek())
                upd.lhist.poke(predInfo.lhist.peek())

                // -------- BTB update --------
                val btb = dut.io.wb1_l1_btb_update(i)

                btb.valid.poke(true.B)
                btb.pc.poke(pc.U)

                val isCall = instr.isCall
                val isReturn = instr.isReturn

                val realTarget =
                  if (isReturn && goldenRAS.nonEmpty) goldenRAS.top
                  else instr.target

                btb.target.poke(realTarget.U)
                btb.isCall.poke(isCall.B)
                btb.isReturn.poke(isReturn.B)

                if (isCall) {
                  goldenRAS.push(pc + 4)
                  println(s"[RAS INFO] golden pushed target=0x${(pc+4).toHexString}")
                }

                if (isReturn && goldenRAS.nonEmpty) {

                  val goldenTarget = goldenRAS.top   // ⭐ 不 pop

                  val predTarget = predInfo.target.peek().litValue.toLong

                  rasTotalCnt += 1

                  if (predTarget == goldenTarget) {
                    rasCorrectCnt += 1
                  } else {
                    println(s"[RAS ERROR] pc=0x${pc.toHexString} pred=0x${predTarget.toHexString} golden=0x${goldenTarget.toHexString}")
                  }

                  goldenRAS.pop()   // ⭐ 最后再 pop
                  println(s"[RAS INFO] golden poped target=${goldenTarget.toHexString}")
                }

                // -------- redirect --------
                if (misPred && !redirect) {
                  redirect = true
                  val isCall = instr.isCall
                  val isReturn = instr.isReturn

                  redirectPC =
                    if (isReturn && goldenRAS.nonEmpty) goldenRAS.top
                    else if (realTaken) instr.target
                    else pc + 4

                  currentPC = pc
                  commit_isCall = isCall
                  commit_isReturn = isReturn
                }

                statQ.enqueue((predTaken, realTaken))

              } else {
                dut.io.wb1_predictor_update(i).valid.poke(false.B)
                dut.io.wb1_l1_btb_update(i).valid.poke(false.B)
              }

            } else {
              dut.io.wb1_predictor_update(i).valid.poke(false.B)
              dut.io.wb1_l1_btb_update(i).valid.poke(false.B)
            }
          }
        } else {
          for (i <- 0 until fetchWidth) {
            dut.io.wb1_predictor_update(i).valid.poke(false.B)
            dut.io.wb1_l1_btb_update(i).valid.poke(false.B)
          }
        }

        // =========================
        // redirect（关键修复点）
        // =========================
        if (redirect) {
          dut.io.commit_redirect.flush.poke(true.B)
          dut.io.commit_redirect.flush_pc.poke(redirectPC.U)
          dut.io.commit_redirect.pc.poke(currentPC.U)
          dut.io.commit_redirect.isCall.poke(commit_isCall.B)
          dut.io.commit_redirect.isReturn.poke(commit_isReturn.B)

          println(s"commit redirect at cycle[${cycle}]: pc 0x${currentPC.toHexString} redirectPC: 0x${redirectPC.toHexString}")
          // ❗只 flush pipeline depth
          for (_ <- 0 until PIPELINE_LAT if commitQ.nonEmpty) {
            commitQ.dequeue()
          }
        }

        // =========================
        // 延迟统计
        // =========================
        if (statQ.size > TRAIN_LAT) {
          val (predTaken, realTaken) = statQ.dequeue()
          total += 1
          if (predTaken == realTaken) correct += 1
        }

        // =========================
        // step
        // =========================
        dut.clock.step()

        if (cycle % 50 == 0 && total > 0) {
          val l0_btb_hitCount = dut.io.l0btbhitCount.peek().litValue.longValue
          val l1_btb_hitCount = dut.io.l1btbhitCount.peek().litValue.longValue
          val btb_accessCount = dut.io.btbaccessCount.peek().litValue.longValue
          val l0_hitrate = (l0_btb_hitCount + 0.0) / btb_accessCount * 100.0
          val l1_hitrate = (l1_btb_hitCount + 0.0) / btb_accessCount * 100.0
          println(s"[cycle=$cycle] acc=${correct.toDouble / total}")
          println(s"[cycle=$cycle] l0_btb_hitRate=${l0_hitrate}%")
          println(s"[cycle=$cycle] l1_btb_hitRate=${l1_hitrate}%")
          val rasAcc =
            if (rasTotalCnt == 0) 0.0
            else rasCorrectCnt.toDouble / rasTotalCnt

          println(s"[cycle=$cycle] RAS acc=$rasAcc, rastotalCnt=${rasTotalCnt}")
          println("")
        }
      }

      println(s"Final accuracy = ${correct.toDouble / total}")
    }
  }
}


