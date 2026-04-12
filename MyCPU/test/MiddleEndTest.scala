package MyCPU.test

import chisel3._
import chiseltest._
import org.scalatest.freespec.AnyFreeSpec

import org.chipsalliance.cde.config._
import MyCPU.middleend._
import MyCPU.config._
import MyCPU.io._

import scala.collection.mutable
import scala.collection.mutable.Queue
import scala.util.Random

class MiddleEndFullSystemTest extends AnyFreeSpec with ChiselScalatestTester {

  implicit val p: Parameters =
    new BTBConfig ++ new BaseConfig ++
    new GSharePredictorConfig ++ new L1CacheConfig ++
    new UseSv32Config ++ new UseCustomLibraryConfig

  "ROB + Exception + Branch FULL Verification" in {
    test(new MiddleEnd) { dut =>

      // =========================
      // 初始化 DUT
      // =========================
      dut.io.stall_from_backpipe.poke(false.B)

      def clearRedirect(): Unit = {
        dut.io.commit_redirect.flush.poke(false.B)
        dut.io.commit_redirect.flush_pc.poke(0.U)
        dut.io.commit_redirect.pc.poke(0.U)
        dut.io.commit_redirect.isCall.poke(false.B)
        dut.io.commit_redirect.isReturn.poke(false.B)
        dut.io.commit_redirect.rasTopPtr.poke(0.U)
      }

      clearRedirect()

      dut.io.commit_refill_write_back.valid.poke(false.B)

      for (i <- 0 until p(FETCH_WIDTH)) {
        dut.io.commit_spec_write_back(i).valid.poke(false.B)
        dut.io.commit_spec_write_back(i).preg.poke(0.U)
      }

      // =========================
      // RAT 模型
      // =========================
      val commitRAT = Array.tabulate(32)(i => i)
      var specRAT   = commitRAT.clone()

      // =========================
      // 物理寄存器状态
      // =========================
      val allocated = mutable.Set[Int]()
      for (i <- 0 until 32) allocated += i

      // =========================
      // ROB Entry
      // =========================
      case class ROBEntry(
        rd: Int,
        pd: Int,
        old: Int,
        hasRd: Boolean,
        isBranch: Boolean,
        mispredict: Boolean,
        isException: Boolean,
        delay: Int
      )

      val rob = new Queue[ROBEntry]()
      val execLatency = 5

      var fireCnt = 0
      var commitCnt = 0
      var flushCnt = 0

      // =========================
      // 主循环
      // =========================
      for (cycle <- 0 until 8000) {

        // =====================
        // 输入指令（混合）
        // =====================
        for (i <- 0 until p(FETCH_WIDTH)) {

          val isBranch = Random.nextInt(10) == 0

          val inst =
            if (isBranch) "h00000063".U // beq x0,x0,0（模拟branch）
            else "h00c58533".U          // add

          dut.io.if5_inst_i(i).poke(inst)
          dut.io.if5_instValid_i(i).poke(true.B)
          dut.io.if5_pc_vec_i(i).poke((cycle * 4 + i * 4).U)
        }

        // =====================
        // 默认无 flush
        // =====================
        clearRedirect()

        dut.clock.step(1)

        // =====================
        // rename 捕获
        // =====================
        for (i <- 0 until p(FETCH_WIDTH)) {

          val u = dut.io.alu_uops(i)

          if (u.valid.peek().litToBoolean) {

            val rd  = u.rd.peek().litValue.toInt
            val pd  = u.pd.peek().litValue.toInt
            val old = u.old_pd.peek().litValue.toInt
            val has = u.HasRd.peek().litToBoolean

            val isBranch = Random.nextInt(10) == 0
            val mispred  = isBranch && Random.nextBoolean()
            val except   = (!isBranch) && Random.nextInt(50) == 0

            if (has && rd != 0) {

              val goldenOld = specRAT(rd)

              // 🔥 invariant 1：old_pd 正确
              assert(old == goldenOld,
                s"[BUG] old_pd mismatch rd=$rd")

              // 🔥 invariant 2：pd 不重复
              assert(!allocated.contains(pd),
                s"[BUG] pd $pd allocated twice")

              allocated += pd
              specRAT(rd) = pd
            }

            rob.enqueue(ROBEntry(
              rd, pd, old, has,
              isBranch, mispred, except,
              execLatency
            ))

            fireCnt += 1
          }
        }

        // =====================
        // 执行推进
        // =====================
        for (i <- rob.indices) {
          val e = rob(i)
          rob(i) = e.copy(delay = e.delay - 1)
        }

        // =====================
        // commit（顺序）
        // =====================
        var didFlush = false

        for (i <- 0 until p(FETCH_WIDTH)) {

          val req = dut.io.commit_spec_write_back(i)

          if (!didFlush && rob.nonEmpty && rob.front.delay <= 0) {

            val e = rob.front

            // =====================
            // 分支错误 or 异常
            // =====================
            if (e.mispredict || e.isException) {

              // 🔥 触发 flush
              dut.io.commit_redirect.flush.poke(true.B)
              dut.io.commit_redirect.flush_pc.poke(0.U)

              // golden 恢复
              specRAT = commitRAT.clone()
              rob.clear()

              didFlush = true
              flushCnt += 1

              req.valid.poke(false.B)

            } else {

              val u = rob.dequeue()

              req.valid.poke(u.hasRd.B)
              req.preg.poke(u.old.U)

              if (u.hasRd && u.rd != 0) {

                // 🔥 invariant 3：释放合法
                assert(allocated.contains(u.old),
                  s"[BUG] free invalid pd ${u.old}")

                allocated -= u.old
                commitRAT(u.rd) = u.pd
              }

              commitCnt += 1
            }

          } else {
            req.valid.poke(false.B)
          }
        }

        // =====================
        // backpressure
        // =====================
        dut.io.stall_from_backpipe.poke(rob.size > 128)

        // =====================
        // debug
        // =====================
        if (cycle % 1000 == 0) {
          println(
            s"[DBG] cyc=$cycle fire=$fireCnt commit=$commitCnt flush=$flushCnt rob=${rob.size}"
          )
        }
      }

      println(s"fire=$fireCnt commit=$commitCnt flush=$flushCnt")

      // =====================
      // 最终检查
      // =====================
      assert(fireCnt > 2000)
      assert(commitCnt > 1000)
    }
  }
}
