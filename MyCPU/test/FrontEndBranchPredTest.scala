package MyCPU.test

import chisel3._
import chiseltest._
import org.scalatest.freespec.AnyFreeSpec
import scala.util.Random
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
    isBranch: Boolean = false,
    taken: Boolean = false,
    target: Long = 0
  )

  "Frontend Branch Prediction (Correct Redirect Model)" in {

    test(new FrontEnd).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

      val fetchWidth = p(FETCH_WIDTH)

      // =========================
      // trace（强规律）
      // =========================
      val trace = (0 until 512).map { i =>
        val pc = 0x1000L   // 固定！！！

        val taken = (i % 2 == 0)

        Instr(pc, isBranch = true, taken = taken)
      }


      def getInstr(pc: Long): Instr =
        trace((pc / 4 % trace.length).toInt)

      // =========================
      // pipeline model
      // =========================
      val commitQ = Queue[Seq[(Long, BranchPredInfo)]]()

      val PIPELINE_LAT = 6   // IF0→IF5
      val TRAIN_LAT = 4      // WB1→WB3

      val statQ = Queue[(Boolean, Boolean)]()

      var correct = 0
      var total = 0

      // =========================
      // init
      // =========================
      dut.io.icache_stall_req.poke(false.B)
      dut.io.stall_from_backpipe.poke(false.B)

      dut.io.commit_redirect.flush.poke(false.B)
      dut.io.commit_redirect.flush_pc.poke(0.U)

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
                btb.target.poke(instr.target.U)
                btb.isCall.poke(false.B)
                btb.isReturn.poke(false.B)

                // -------- redirect --------
                if (misPred && !redirect) {
                  redirect = true
                  redirectPC =
                    if (realTaken) instr.target
                    else pc + 4
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
          println(s"[cycle=$cycle] acc=${correct.toDouble / total}")
        }
      }

      println(s"Final accuracy = ${correct.toDouble / total}")
    }
  }
}

