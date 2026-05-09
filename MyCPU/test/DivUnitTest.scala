import chisel3._
import chiseltest._
import org.scalatest.freespec.AnyFreeSpec
import MyCPU.backend._
import MyCPU.config._
import org.chipsalliance.cde.config._
import scala.util.Random

class DivUnitFreeSpecTest extends AnyFreeSpec with ChiselScalatestTester {

  "DivUnit should perform DIV, DIVU, REM, REMU correctly" in {
    implicit val p: Parameters = (new BTBConfig ++ new BaseConfig ++
      new GSharePredictorConfig ++ new L1CacheConfig ++
      new UseSv32Config ++ new UseCustomLibraryConfig ++
      new StoreSetConfig).asInstanceOf[Parameters]

    // helper: 将 Scala Int 转成 32-bit UInt 补码
    def intToUInt32(x: Int): BigInt = if (x < 0) BigInt(1L << 32) + x else BigInt(x)

    // helper: 打印指令名字
    def instrName(f3: Int): String = f3 match {
      case 4 => "DIV"
      case 5 => "DIVU"
      case 6 => "REM"
      case 7 => "REMU"
    }

    test(new DivUnit).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

      def runTest(dividend: Int, divisor: Int, funct3: Int): Unit = {
        val isSigned = funct3 == 4 || funct3 == 6 // DIV or REM
        val isDiv = funct3 == 4 || funct3 == 5   // DIV or DIVU
        val isRem = funct3 == 6 || funct3 == 7   // REM or REMU

        // poke 输入
        dut.io.valid.poke(true.B)
        dut.io.flush.poke(false.B)
        dut.io.stall_from_backpipe.poke(false.B)
        dut.io.funct3.poke(funct3.U)
        dut.io.data1.poke(intToUInt32(dividend).U)
        dut.io.data2.poke(intToUInt32(divisor).U)

        // 等待 divReady
        var cycles = 0
        while (!dut.io.divReady.peek().litToBoolean && cycles < 40) {
          dut.clock.step(1)
          cycles += 1
        }

        // 计算期望值
        val (expectedDiv, expectedRem) =
          if (divisor == 0) {
            (0xFFFFFFFF, intToUInt32(dividend).toInt)
          } else if (isSigned) {
            val d = dividend / divisor
            val r = dividend % divisor
            (intToUInt32(d).toInt, intToUInt32(r).toInt) // 补码 UInt
          } else {
            val udividend = dividend.toLong & 0xFFFFFFFFL
            val udivisor  = divisor.toLong & 0xFFFFFFFFL
            ((udividend / udivisor).toInt, (udividend % udivisor).toInt)
          }

        // 打印信息
        println(s"Instr: ${instrName(funct3)}, dividend: $dividend, divisor: $divisor, " +
          s"diveendUInt32: ${intToUInt32(dividend)}, divisorUInt32: ${intToUInt32(divisor)}" +
          s"\ncycle: $cycles, div: ${dut.io.divResult.peek().litValue}, rem: ${dut.io.remResult.peek().litValue}, " +
          s"expected div: $expectedDiv, expected rem: $expectedRem")

        // check 输出
        if (isDiv) dut.io.divResult.expect(intToUInt32(expectedDiv).U)
        if (isRem) dut.io.remResult.expect(intToUInt32(expectedRem).U)
        dut.io.resValid.expect(true.B)
        dut.clock.step(1)
      }

      // 测试向量（包括负数）
      val vectors = Seq(
        (10, 2, 4),    // DIV
        (-10, 2, 4),   // DIV
        (10, -3, 4),   // DIV
        (-10, -3, 4),  // DIV
        (10, 3, 5),    // DIVU
        (0xFFFFFFFF, 2, 5), // DIVU
        (10, 0, 4),    // DIV0
        (10, 3, 6),    // REM
        (-10, 3, 6),   // REM
        (10, 3, 7)     // REMU
      )

      vectors.foreach { case (a, b, f3) => runTest(a, b, f3) }

      // 随机测试
      val rand = new Random()
      for (_ <- 0 until 200) {
        val dividend = rand.nextInt()
        val divisor = rand.nextInt()
        if (divisor != 0) {
          val f3 = rand.nextInt(4) match {
            case 0 => 4 // DIV
            case 1 => 5 // DIVU
            case 2 => 6 // REM
            case _ => 7 // REMU
          }
          runTest(dividend, divisor, f3)
        }
      }
    }
  }
}