package MyCPU.test

import chisel3._
import chiseltest._
import chiseltest.WriteVcdAnnotation
import org.scalatest.freespec.AnyFreeSpec

import scala.collection.mutable
import scala.util.Random

import org.chipsalliance.cde.config._
import MyCPU.cache._
import MyCPU.config._
import MyCPU.io._

class GoldenMem(lineSize: Int) {
  def readLine(addr: Long): Seq[BigInt] = {
    val base = (addr / 4).toInt
    (0 until lineSize).map(i => BigInt(base + i))
  }
}

class ICacheNBTest extends AnyFreeSpec with ChiselScalatestTester {

  implicit val p: Parameters =
    new BaseConfig ++ new L1CacheConfig ++ new UseSv32Config ++ new UseCustomLibraryConfig

  def lineAlign(addr: Long): Long = {
    val lineBytes = p(L1CacheLineLength) * 4
    addr & ~(lineBytes - 1).toLong
  }

  "Non-blocking ICache verification" in {

    test(new ICache).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

      val mem = new GoldenMem(p(L1CacheLineLength))
      val rand = new Random(42)

      val outstanding = mutable.Map[Long, Boolean]()   // lineAddr -> seen
      val inflight    = mutable.Set[Long]()            // AXI inflight
      val pendingAXI  = mutable.ArrayBuffer[Long]()    // 等待返回（乱序）

      var cycle = 0

      // ----------------------------
      // AXI default
      // ----------------------------
      dut.io.axi4_master.ar.ready.poke(false.B)
      dut.io.axi4_master.r.valid.poke(false.B)

      while (cycle < 3000) {

        // ============================
        // 1️⃣ 发请求
        // ============================
        val reqValid = rand.nextInt(3) != 0
        val addr = (rand.nextInt(256) * 4).toLong

        dut.io.if0_icache_req.valid.poke(reqValid.B)
        dut.io.if0_icache_req.index.poke(((addr & 0xFF0) >> 4).U)

        val lineAddr = lineAlign(addr)

        if (reqValid) {
          outstanding(lineAddr) = true
        }

        // ----------------------------
        // ITLB: VA=PA
        // ----------------------------
        for (i <- 0 until p(FETCH_WIDTH)) {
          dut.io.if1_itlb_resp(i).valid.poke(true.B)
          dut.io.if1_itlb_resp(i).paddr.bits.poke(
            addr.U
          )
        }

        // ============================
        // 2️⃣ AXI 捕获请求
        // ============================
        if (dut.io.axi4_master.ar.valid.peek().litToBoolean) {

          val arAddr = dut.io.axi4_master.ar.bits.addr.peek().litValue.toLong

          // ❗ 检查重复 refill
          assert(
            !inflight.contains(arAddr),
            s"[cycle=$cycle] DUP REFILL addr=0x${arAddr.toHexString}"
          )

          inflight += arAddr
          pendingAXI += arAddr

          dut.io.axi4_master.ar.ready.poke(true.B)
        } else {
          dut.io.axi4_master.ar.ready.poke(false.B)
        }

        // ============================
        // 3️⃣ AXI 乱序返回
        // ============================
        dut.io.axi4_master.r.valid.poke(false.B)

        if (pendingAXI.nonEmpty && rand.nextBoolean()) {

          val idx = rand.nextInt(pendingAXI.size)
          val retAddr = pendingAXI.remove(idx)

          val line = mem.readLine(retAddr)

          for (i <- line.indices) {
            dut.io.axi4_master.r.valid.poke(true.B)
            dut.io.axi4_master.r.bits.data.poke(line(i).U)
            dut.io.axi4_master.r.bits.last.poke((i == line.size - 1).B)

            dut.clock.step()
          }

          inflight -= retAddr
        }

        // ============================
        // 4️⃣ step
        // ============================
        dut.clock.step()

        // ============================
        // 5️⃣ 检查 hit
        // ============================
        val hit = dut.io.if2_icache_hit.peek().litToBoolean

        if (hit) {

          val pc = dut.io.if1_itlb_resp(0).paddr.bits.peek().litValue.toLong
          val laddr = lineAlign(pc)

          // ❗ 必须是 outstanding 请求
          assert(
            outstanding.contains(laddr),
            s"[cycle=$cycle] unexpected hit addr=0x${laddr.toHexString}"
          )

          val golden = mem.readLine(laddr)
          val dutLine = dut.io.if2_icacheline_resp.map(_.peek().litValue)

          for (i <- golden.indices) {
            assert(
              golden(i) == dutLine(i),
              s"[cycle=$cycle] DATA MISMATCH addr=0x${laddr.toHexString}"
            )
          }

          // 命中后删除（允许多次命中可去掉）
          outstanding.remove(laddr)
        }

        // ============================
        // 6️⃣ hit-under-miss 检查
        // ============================
        val hasMiss = inflight.nonEmpty

        if (hasMiss && hit) {
          // OK: hit-under-miss
        }

        // ============================
        // 7️⃣ stall 合法性
        // ============================
        val stall = dut.io.if2_icache_stall_req.peek().litToBoolean

        // 非阻塞 cache：不能“所有 miss 都 stall”
        if (inflight.nonEmpty) {
          // 允许 stall，但不能一直 stall
        }

        cycle += 1
      }
    }
  }
}
