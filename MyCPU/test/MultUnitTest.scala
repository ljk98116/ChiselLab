// import chisel3._
// import chiseltest._
// import org.scalatest.freespec.AnyFreeSpec
// import MyCPU.backend._
// import MyCPU.config._  // 确保 Funct3Val 定义可用
// import org.chipsalliance.cde.config._
// import scala.util.Random

// class MultUnitFreeSpecTest extends AnyFreeSpec with ChiselScalatestTester {

//   "MultUnit should compute correct multiplication for various inputs" in {

//     implicit val p: Parameters = (new BTBConfig ++ new BaseConfig ++
//         new GSharePredictorConfig ++ new L1CacheConfig ++ new UseSv32Config ++
//         new UseCustomLibraryConfig ++ new StoreSetConfig).asInstanceOf[Parameters]

//     test(new MultUnit).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
//       val width = p(DATA_WIDTH)

//       // Helper function: compute expected result in Scala
//       def expectedMul(a: BigInt, b: BigInt, width: Int, funct3: Int): BigInt = {
//         val mask = (BigInt(1) << width) - 1

//         val signedA = if ((a & (BigInt(1) << (width - 1))) != 0) a | (~mask) else a
//         val signedB = if ((b & (BigInt(1) << (width - 1))) != 0) b | (~mask) else b

//         val prod: BigInt = funct3 match {
//           case 0 => signedA * signedB                 // MUL: full product
//           case 1 => signedA * signedB                 // MULH: signed * signed
//           case 2 => signedA * b                        // MULHSU: signed * unsigned
//           case 3 => a * b                               // MULHU: unsigned * unsigned
//         }

//         funct3 match {
//           case 0 => prod & mask                        // MUL: lower width bits
//           case 1 | 2 | 3 => (prod >> width) & mask     // 高位取宽度位
//         }
//       }

//       val mask = (BigInt(1) << width) - 1
//       val signBit = BigInt(1) << (width - 1)
//       def U(x: BigInt): BigInt = x & mask

//       val directedVectors = Seq(
//         (BigInt(1), BigInt(1)),
//         (BigInt(5), BigInt(7)),
//         (BigInt(12345), BigInt(6789)),
//         (BigInt(0xFFFFFFF4L), BigInt(89)),
//         (BigInt(0xFFFFFFFFL), BigInt(2)),
//         (BigInt(0x7FFFFFFFL), BigInt(0x7FFFFFFFL)),

//         // signed/unsigned corner cases
//         (BigInt(0), BigInt(0)),
//         (BigInt(0), mask),
//         (mask, BigInt(0)),
//         (mask, mask),                       // -1 * -1 / unsigned max * max
//         (signBit, signBit),                 // INT_MIN * INT_MIN
//         (signBit, mask),                    // INT_MIN * -1 / unsigned max
//         (mask, signBit),
//         (signBit, BigInt(1)),
//         (BigInt(1), signBit),
//         (signBit - 1, signBit - 1),         // INT_MAX * INT_MAX
//         (signBit - 1, mask),
//         (mask, signBit - 1),

//         // bit-pattern cases likely to exercise many Booth partial products
//         (BigInt("55555555", 16), BigInt("AAAAAAAA", 16)),
//         (BigInt("AAAAAAAA", 16), BigInt("55555555", 16)),
//         (BigInt("33333333", 16), BigInt("CCCCCCCC", 16)),
//         (BigInt("0F0F0F0F", 16), BigInt("F0F0F0F0", 16)),
//         (BigInt("00FF00FF", 16), BigInt("FF00FF00", 16)),
//         (BigInt("00010001", 16), BigInt("80008000", 16))
//       ).map { case (a, b) => (U(a), U(b)) }

//       val boundaryValues = Seq(
//         BigInt(0),
//         BigInt(1),
//         BigInt(2),
//         BigInt(3),
//         signBit - 2,
//         signBit - 1,
//         signBit,
//         signBit + 1,
//         mask - 2,
//         mask - 1,
//         mask
//       ).map(U)

//       val boundaryVectors = for {
//         a <- boundaryValues
//         b <- boundaryValues
//       } yield (a, b)

//       val random = new Random(0xC0FFEE)
//       val randomVectors = Seq.fill(200) {
//         (BigInt(width, random), BigInt(width, random))
//       }

//       val testVectors = (directedVectors ++ boundaryVectors ++ randomVectors).distinct

//       val funct3List = Seq(0, 1, 2, 3) // MUL, MULH, MULHSU, MULHU

//       for ((a, b) <- testVectors) {
//         for (f3 <- funct3List) {
//           // 设置输入
//           dut.io.mult_input_info.valid.poke(true.B)
//           dut.io.mult_input_info.funct3.poke(f3.U)
//           dut.io.mult_input_info.data1.poke(a.U)
//           dut.io.mult_input_info.data2.poke(b.U)
//           dut.io.flush.poke(false.B)
//           dut.io.stall_from_backpipe.poke(false.B)

//           // 等待 MultUnit 完成。CSA3/CSA4 实现的 latency 可能不同，给足余量。
//           var cycle = 0
//           val maxCycles = 32
//           while (!dut.io.multReady.peek().litToBoolean && cycle < maxCycles) {
//             dut.clock.step(1)
//             cycle += 1
//           }
//           assert(cycle < maxCycles, s"MultUnit timeout: a=0x${a.toString(16)}, b=0x${b.toString(16)}, funct3=$f3")

//           val res = dut.io.result.peek().litValue
//           val expected = expectedMul(a, b, width, f3)
//           val instrName = f3 match {
//             case 0 => "MUL"
//             case 1 => "MULH"
//             case 2 => "MULHSU"
//             case 3 => "MULHU"
//           }

//           println(s"Instr: $instrName, cycle: $cycle, a=0x${a.toString(16)}, b=0x${b.toString(16)} => result: 0x${res.toString(16)}, expected: 0x${expected.toString(16)}")
//           assert(res == expected,
//             s"$instrName failed: a=0x${a.toString(16)}, b=0x${b.toString(16)}, got 0x${res.toString(16)}, expected 0x${expected.toString(16)}")

//           // 等一拍再测试下一条指令
//           dut.clock.step(1)
//         }
//       }
//     }
//   }
// }