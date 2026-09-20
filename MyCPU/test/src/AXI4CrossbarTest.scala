package MyCPU.test

import chisel3._
import chiseltest._
import org.scalatest.freespec.AnyFreeSpec
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

class AXI4CrossbarTest extends AnyFreeSpec with ChiselScalatestTester {

  implicit val p: Parameters =
    new RV64BTBConfig ++ new BaseRV64IConfig ++
    new GSharePredictorConfig ++ new L1CacheConfig ++
    new UseSv32Config ++ new UseCustomLibraryConfig

  private val NUM_MASTER = 2
  private val NUM_SLAVE  = 4

  // ── slave 端口（Flipped）：只能 poke Input 方向 ──
  //   可以 poke: ar/aw/w.valid, b/r.ready
  //   不能 poke: ar/aw/w.ready, b/r.valid
  private def idleAllSlaves(dut: AXI4Crossbar): Unit =
    for (s <- dut.io.slave_axi4s.indices) {
      dut.io.slave_axi4s(s).ar.valid.poke(false.B)
      dut.io.slave_axi4s(s).aw.valid.poke(false.B)
      dut.io.slave_axi4s(s).w.valid.poke(false.B)
      dut.io.slave_axi4s(s).b.ready.poke(false.B)
      dut.io.slave_axi4s(s).r.ready.poke(false.B)
    }

  // ── master 端口（未翻转）：只能 poke Input 方向 ──
  //   可以 poke: ar/aw/w.ready, r.valid, b.valid
  //   不能 poke: ar/aw/w.valid, r.ready, b.ready
  private def idleAllMasters(dut: AXI4Crossbar): Unit =
    for (m <- dut.io.master_axi4s.indices) {
      dut.io.master_axi4s(m).ar.ready.poke(false.B)
      dut.io.master_axi4s(m).aw.ready.poke(false.B)
      dut.io.master_axi4s(m).w.ready.poke(false.B)
      dut.io.master_axi4s(m).r.valid.poke(false.B)
      dut.io.master_axi4s(m).b.valid.poke(false.B)
    }

  private def resetDut(dut: AXI4Crossbar): Unit = {
    idleAllSlaves(dut)
    idleAllMasters(dut)
    dut.reset.poke(true.B)
    dut.clock.step()
    dut.reset.poke(false.B)
    dut.clock.step()
  }

  // ═════════════════════════════════════════════════════════
  //  1) 复位后所有 slave 空闲：master 端不应出现 valid
  // ═════════════════════════════════════════════════════════
  "AXI4Crossbar idle after reset" in {
    test(new AXI4Crossbar(NUM_MASTER, NUM_SLAVE)) { dut =>
      resetDut(dut)
      for (_ <- 0 until 8) {
        dut.clock.step()
        dut.io.master_axi4s(0).ar.valid.expect(false.B)
        dut.io.master_axi4s(1).ar.valid.expect(false.B)
        dut.io.master_axi4s(0).aw.valid.expect(false.B)
        dut.io.master_axi4s(0).w.valid.expect(false.B)
      }
      println("[idle] 8 idle cycles, master.valid stays low")
    }
  }

  // ═════════════════════════════════════════════════════════
  //  2) 单个 slave 有效：master 被路由到该 slave
  // ═════════════════════════════════════════════════════════
  "AXI4Crossbar single valid slave is routed to master" in {
    test(new AXI4Crossbar(1, NUM_SLAVE)) { dut =>
      resetDut(dut)

      // slave 2 发起 AR 请求
      dut.io.slave_axi4s(2).ar.valid.poke(true.B)
      dut.io.slave_axi4s(2).ar.bits.id.poke(3.U)
      dut.io.slave_axi4s(2).ar.bits.addr.poke(0x80000000L.U)
      dut.io.slave_axi4s(2).ar.bits.len.poke(0.U)
      dut.io.slave_axi4s(2).ar.bits.size.poke(3.U)
      dut.io.slave_axi4s(2).ar.bits.burst.poke(1.U)

      dut.clock.step()

      // master 端应看到 valid 和透传的 bits
      dut.io.master_axi4s(0).ar.valid.expect(true.B)
      dut.io.master_axi4s(0).ar.bits.addr.expect(0x80000000L.U)
      dut.io.master_axi4s(0).ar.bits.id.expect(3.U)

      // master 回 ready → 被选中的 slave 2 端 ready 拉高
      dut.io.master_axi4s(0).ar.ready.poke(true.B)
      dut.clock.step()

      dut.io.slave_axi4s(2).ar.ready.expect(true.B)
      dut.io.slave_axi4s(1).ar.ready.expect(false.B)
      dut.io.slave_axi4s(3).ar.ready.expect(false.B)

      println("[single-slave] master 0 routed to slave 2; valid/ready propagated")
    }
  }

  // ═════════════════════════════════════════════════════════
  //  3) 多 slave 同时有效：RR=0 时选最低有效索引
  // ═════════════════════════════════════════════════════════
  "AXI4Crossbar lowest index wins when RR=0" in {
    test(new AXI4Crossbar(1, NUM_SLAVE)) { dut =>
      resetDut(dut)

      dut.io.slave_axi4s(1).ar.valid.poke(true.B)
      dut.io.slave_axi4s(1).ar.bits.addr.poke(0x11110000L.U)
      dut.io.slave_axi4s(3).ar.valid.poke(true.B)
      dut.io.slave_axi4s(3).ar.bits.addr.poke(0x33330000L.U)
      dut.clock.step()

      // RR=0，selMask 最低置位是 bit 1 → slave 1 被选中
      dut.io.master_axi4s(0).ar.valid.expect(true.B)
      dut.io.master_axi4s(0).ar.bits.addr.expect(0x11110000L.U)

      dut.io.master_axi4s(0).ar.ready.poke(true.B)
      dut.clock.step()

      // rrCnt 前进到 1 后，selMask = 0b1010 >> 1 = 0b0101，最低位仍是 slave 1
      dut.io.slave_axi4s(1).ar.ready.expect(true.B)
      dut.io.slave_axi4s(3).ar.ready.expect(false.B)

      println("[lowest-index] slave 1 selected at RR=0")
    }
  }

  // ═════════════════════════════════════════════════════════
  //  4) AW 通道也能触发仲裁
  // ═════════════════════════════════════════════════════════
  "AXI4Crossbar AW channel drives arbitration" in {
    test(new AXI4Crossbar(1, NUM_SLAVE)) { dut =>
      resetDut(dut)

      dut.io.slave_axi4s(3).aw.valid.poke(true.B)
      dut.io.slave_axi4s(3).aw.bits.addr.poke(0xDEAD0000L.U)
      dut.clock.step()

      dut.io.master_axi4s(0).aw.valid.expect(true.B)
      dut.io.master_axi4s(0).aw.bits.addr.expect(0xDEAD0000L.U)

      dut.io.master_axi4s(0).aw.ready.poke(true.B)
      dut.clock.step()

      dut.io.slave_axi4s(3).aw.ready.expect(true.B)

      println("[aw-channel] AW channel drove selection to slave 3")
    }
  }

  // ═════════════════════════════════════════════════════════
  //  5) 多个 master 的 RR 指针相互独立
  //     两个 slave 都发出请求，只让 master 0 fire 一次：
  //       - master 0 的 rrCnt 前进到 1 → 路由到 slave 1
  //       - master 1 的 rrCnt 保持 0   → 仍路由到 slave 0
  // ═════════════════════════════════════════════════════════
  "AXI4Crossbar multiple masters have independent RR pointers" in {
    test(new AXI4Crossbar(NUM_MASTER, NUM_SLAVE)) { dut =>
      resetDut(dut)

      dut.io.slave_axi4s(0).ar.valid.poke(true.B)
      dut.io.slave_axi4s(0).ar.bits.addr.poke(0x00001000L.U)
      dut.io.slave_axi4s(1).ar.valid.poke(true.B)
      dut.io.slave_axi4s(1).ar.bits.addr.poke(0x00002000L.U)

      // 步进前：两个 master 的 rrCnt 都是 0，都选 slave 0
      dut.io.master_axi4s(0).ar.valid.expect(true.B)
      dut.io.master_axi4s(0).ar.bits.addr.expect(0x00001000L.U)
      dut.io.master_axi4s(1).ar.valid.expect(true.B)
      dut.io.master_axi4s(1).ar.bits.addr.expect(0x00001000L.U)

      // 只让 master 0 回 ready 一次 → 只有 master 0 的 rrCnt 前进
      dut.io.master_axi4s(0).ar.ready.poke(true.B)
      dut.clock.step()

      // 步进后：master 0 的 rrCnt = 1 → 选 slave 1；master 1 仍选 slave 0
      dut.io.master_axi4s(0).ar.bits.addr.expect(0x00002000L.U)
      dut.io.master_axi4s(1).ar.bits.addr.expect(0x00001000L.U)

      println("[multi-master] RR pointers independent")
    }
  }

  // ═════════════════════════════════════════════════════════
  //  6) 轮询指针在无 slave 有效时复位为 0
  // ═════════════════════════════════════════════════════════
  "AXI4Crossbar rr pointer resets when no slave active" in {
    test(new AXI4Crossbar(1, NUM_SLAVE)) { dut =>
      resetDut(dut)

      // 让 slave 0 请求并 fire 一次 → rrCnt 从 0 前进到 1
      dut.io.slave_axi4s(0).ar.valid.poke(true.B)
      dut.io.master_axi4s(0).ar.ready.poke(true.B)
      dut.clock.step()
      dut.clock.step()

      // 所有 slave 空闲一拍：selHit = false → rrCnt := 0
      dut.io.slave_axi4s(0).ar.valid.poke(false.B)
      dut.io.master_axi4s(0).ar.ready.poke(false.B)
      dut.clock.step()

      // 重新让 slave 2 请求：指针已复位 → 应选 slave 2
      dut.io.slave_axi4s(2).ar.valid.poke(true.B)
      dut.io.slave_axi4s(2).ar.bits.addr.poke(0x22220000L.U)
      dut.clock.step()

      dut.io.master_axi4s(0).ar.valid.expect(true.B)
      dut.io.master_axi4s(0).ar.bits.addr.expect(0x22220000L.U)

      println("[rr-reset] rr pointer reset to 0; slave 2 routed")
    }
  }
}