import chisel3._
import chiseltest._
import org.scalatest.freespec.AnyFreeSpec
import MyCPU.backend._
import MyCPU.config._  // 确保 Funct3Val 定义可用
import org.chipsalliance.cde.config._

class MultUnitFreeSpecTest extends AnyFreeSpec with ChiselScalatestTester {

  "MultUnit should compute correct multiplication for various inputs" in {

    implicit val p: Parameters = (new BTBConfig ++ new BaseConfig ++
        new GSharePredictorConfig ++ new L1CacheConfig ++ new UseSv32Config ++
        new UseCustomLibraryConfig ++ new StoreSetConfig).asInstanceOf[Parameters]

    test(new MultUnit).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      val width = p(DATA_WIDTH)

      // Helper function: compute expected result in Scala
      def expectedMul(a: BigInt, b: BigInt, width: Int, funct3: Int): BigInt = {
        val mask = (BigInt(1) << width) - 1

        val signedA = if ((a & (BigInt(1) << (width - 1))) != 0) a | (~mask) else a
        val signedB = if ((b & (BigInt(1) << (width - 1))) != 0) b | (~mask) else b

        val prod: BigInt = funct3 match {
          case 0 => signedA * signedB                 // MUL: full product
          case 1 => signedA * signedB                 // MULH: signed * signed
          case 2 => signedA * b                        // MULHSU: signed * unsigned
          case 3 => a * b                               // MULHU: unsigned * unsigned
        }

        funct3 match {
          case 0 => prod & mask                        // MUL: lower width bits
          case 1 | 2 | 3 => (prod >> width) & mask     // 高位取宽度位
        }
      }

      val testVectors = Seq(
        (1L, 1L),
        (5L, 7L),
        (12345L, 6789L),
        (0xFFFFFFF4L, 89L),
        (0xFFFFFFFFL, 2L),
        (0x7FFFFFFFL, 0x7FFFFFFFL)
      )

      val funct3List = Seq(0, 1, 2, 3) // MUL, MULH, MULHSU, MULHU

      for ((a, b) <- testVectors) {
        for (f3 <- funct3List) {
          // 设置输入
          dut.io.valid.poke(true.B)
          dut.io.funct3.poke(f3.U)
          dut.io.data1.poke(a.U)
          dut.io.data2.poke(b.U)
          dut.io.flush.poke(false.B)
          dut.io.stall_from_backpipe.poke(false.B)

          // 等待 MultUnit 完成（最多 10 个周期）
          var cycle = 0
          while (!dut.io.multReady.peek().litToBoolean && !dut.io.resValid.peek().litToBoolean && cycle < 10) {
            dut.clock.step(1)
            cycle += 1
          }

          val res = dut.io.result.peek().litValue
          val expected = expectedMul(a, b, width, f3)
          val instrName = f3 match {
            case 0 => "MUL"
            case 1 => "MULH"
            case 2 => "MULHSU"
            case 3 => "MULHU"
          }

          println(s"Instr: $instrName, cycle: $cycle, $a * $b = result: $res, expected: $expected")
          assert(res == expected, s"$instrName failed: got $res, expected $expected")

          // 等一拍再测试下一条指令
          dut.clock.step(1)
        }
      }
    }
  }
}