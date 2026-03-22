package MyCPU.test

import chisel3._
import chiseltest._
import chiseltest.WriteVcdAnnotation

import org.scalatest.freespec.AnyFreeSpec

import scala.util.Random

import org.chipsalliance.cde.config._

import MyCPU.frontend._
import MyCPU.config._

class FakeIMem(size: Int = 4096) {
  val mem = Array.tabulate(size)(i => i)

  def readLine(addr: Long, lineSize: Int): Seq[Int] = {
    val base = (addr / 4).toInt
    (0 until lineSize).map(i => mem(base + i))
  }
}

class GoldenFrontEnd(fetchWidth: Int) {
	var pc = 0L
	val step = fetchWidth * 4

	val imem = new FakeIMem()

	def stepOnce(
		stall: Boolean,
		redirect: Option[Long],
		icacheMiss: Boolean
	): Seq[(Long, Int)] = {
		// 1️⃣ 处理 redirect
		if (redirect.isDefined) {
			pc = redirect.get
		}

		val curPC = pc   // ✅ 先保存当前 PC

		// 2️⃣ 决定是否推进 PC
		if (!stall && redirect.isEmpty) {
			pc += step
		}

		// 3️⃣ miss → 不输出
		if (icacheMiss) return Seq()

		val line = imem.readLine(curPC, fetchWidth)

		(0 until fetchWidth).map { i =>
			val instPC = curPC + i * 4
			(instPC, line(i))
		}
	}

}

case class Trace(
  cycle: Int,
  stall: Boolean,
  redirect: Boolean,
  pcOut: Seq[Long],
  validOut: Seq[Boolean]
)


class FrontEndBaseTest extends AnyFreeSpec with ChiselScalatestTester {

  implicit val p: Parameters =
    new BTBConfig ++ new BaseConfig ++
    new GSharePredictorConfig ++ new L1CacheConfig ++
    new UseSv32Config ++
    new UseCustomLibraryConfig

	val traceBuf = scala.collection.mutable.Queue[Trace]()
	val TRACE_DEPTH = 100

	def dumpTraceAndFail(msg: String): Unit = {
		println("\n===== LAST 100 CYCLES TRACE =====")
		traceBuf.foreach { t =>
			println(f"[cycle=${t.cycle}%4d] " +
			s"stall=${t.stall} redirect=${t.redirect} " +
			s"valid=${t.validOut.mkString} " +
			s"pc=${t.pcOut.mkString(",")}")
		}
		println("=================================\n")

		fail(msg)
	}
	
  	"Frontend FIFO-based verification" in {

    test(new FrontEnd).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

		Random.setSeed(42)

		val fetchWidth = p(FETCH_WIDTH)
		val golden = new GoldenFrontEnd(fetchWidth)

		val goldenQ = scala.collection.mutable.Queue[Seq[Long]]()
		val dutQ    = scala.collection.mutable.Queue[Long]()

      	val dutSeen = scala.collection.mutable.Set[Long]()

      	val BUFFER_SIZE = 16

      	var cycle = 0

		// 初始化输入（第0拍）
		dut.io.stall_from_backpipe.poke(false.B)

		while (cycle < 300) {
			// ========================
			// 1️⃣ 先推进时钟（进入新拍）
			// ========================
			dut.clock.step()

			// ========================
			// 2️⃣ 读取 DUT 输出（这一拍的结果）
			// ========================
			val dutOut =
				(0 until fetchWidth).flatMap { i =>
				val valid = dut.io.if5_instValid(i).peek().litToBoolean
				if (valid) {
					Some(dut.io.if5_pc_vec(i).peek().litValue)
				} else None
				}

			// 👉 在这里做所有 check（FIFO / 顺序 / 重复）
			// ------------------------ 
			// 检查 1：重复 
			// ------------------------ 
			dutOut.foreach { 
				pc => assert(!dutSeen.contains(pc.longValue), s"[cycle=$cycle] DUP PC = $pc") 
				dutSeen += pc.longValue 
			} 
			// ------------------------ 
			// 检查 2：顺序 
			// ------------------------ 
			if (dutOut.size >= 2) { 
				for (i <- 1 until dutOut.size) { 
					assert( dutOut(i) == dutOut(i - 1) + 4, s"[cycle=$cycle] OUT-OF-ORDER ${dutOut(i-1)} -> ${dutOut(i)}" ) 
				} 
			} 
			// ------------------------ 
			// 检查 3：FIFO 对拍（核心） 
			// ------------------------ 
			while (goldenQ.nonEmpty && dutQ.nonEmpty) { 
				val bundle = goldenQ.front 
				for (pc <- bundle) { 
					if (dutQ.nonEmpty) { 
						val d = dutQ.dequeue() 
						assert(pc == d, s"[cycle=$cycle] mismatch g=$pc d=$d") 
					} 
				} 
				goldenQ.dequeue() 
			} 

			// ------------------------ 
			// 检查 5：窗口限制 
			// ------------------------ 
			assert( goldenQ.size <= BUFFER_SIZE, s"[cycle=$cycle] Golden overflow" ) 
			assert( dutQ.size <= BUFFER_SIZE, s"[cycle=$cycle] DUT overflow" )

			// ========================
			// 3️⃣ 生成“下一拍输入”
			// ========================
			val stall        = Random.nextInt(4) == 0
			val doRedirect   = Random.nextInt(20) == 0
			val redirectPC   = (Random.nextInt(256) * 4).toLong
			val icacheMiss   = Random.nextInt(5) == 0

			// ========================
			// 4️⃣ Golden 用“当前拍输入”
			// ========================
			val goldenOut = golden.stepOnce(
				stall = stall,
				redirect = if (doRedirect) Some(redirectPC) else None,
				icacheMiss = icacheMiss
			)

			// ========================
			// 5️⃣ poke（作用于下一拍）
			// ========================
			dut.io.stall_from_backpipe.poke(stall.B)
			dut.io.icache_stall_req.poke(icacheMiss.B)

			dut.io.commit_redirect.flush.poke(doRedirect.B)
			dut.io.commit_redirect.flush_pc.poke(redirectPC.U)


			val pcVec =
				(0 until fetchWidth).map(i => dut.io.if5_pc_vec(i).peek().litValue)

			val validVec =
				(0 until fetchWidth).map(i => dut.io.if5_instValid(i).peek().litToBoolean)

			traceBuf.enqueue(
				Trace(
					cycle = cycle,
					stall = stall,
					redirect = doRedirect,
					pcOut = pcVec.map(_.longValue),
					validOut = validVec
				)
			)

			// 保持长度 <=100
			if (traceBuf.size > TRACE_DEPTH) {
				traceBuf.dequeue()
			}

			cycle += 1
		}
	}
  }
}
