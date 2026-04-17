package MyCPU.middleend

import chisel3._
import chiseltest._
import org.scalatest.freespec.AnyFreeSpec
import org.chipsalliance.cde.config._
import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._
import scala.util.Random
import scala.collection.mutable

class MiddleEndDecodeRenamePipelineTest extends AnyFreeSpec with ChiselScalatestTester {

  implicit val p: Parameters = (new BTBConfig ++ new BaseConfig ++
    new GSharePredictorConfig ++ new L1CacheConfig ++ new UseSv32Config ++
    new UseCustomLibraryConfig ++ new StoreSetConfig).asInstanceOf[Parameters]

  "MiddleEnd decode and rename should handle all patterns correctly with ROB and PRF" in {
    test(new MiddleEnd).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

      val rand = new Random()
      val pipeDepth = 5 // decode → rename1 → rename2 → dispatch1 → dispatch2

      // 初始化 IO
      dut.io.stall_from_backpipe.poke(false.B)
      dut.io.commit_redirect.flush.poke(false.B)
      dut.io.commit_refill_write_back.valid.poke(false.B)
      dut.io.commit_spec_write_back.foreach(_.valid.poke(false.B))
      dut.io.commit_spec_robId_write_back.foreach(_.valid.poke(false.B))
      dut.io.loadRAWHarvard.valid.poke(false.B)

      // 可用物理寄存器和 ROB
      val freePRF = mutable.Queue[Int]((0 until 1 << p(PREG_WIDTH)).toSeq: _*)
      val nextROBId = mutable.Queue[Int]((0 until 1 << p(ROBID_WIDTH)).toSeq: _*)

      val patterns: Seq[(String, BigInt, String)] = Seq(
        ("LUI", 0x00000037, "U"), ("AUIPC", 0x00000017, "U"), ("JAL", 0x0000006F, "J"),
        ("JALR", 0x00000067, "I"), ("BEQ", 0x00000063, "B"), ("BNE", 0x00001063, "B"),
        ("BLT", 0x00004063, "B"), ("BGE", 0x00005063, "B"), ("BLTU", 0x00006063, "B"),
        ("BGEU", 0x00007063, "B"), ("LB", 0x00000003, "I"), ("LH", 0x00001003, "I"),
        ("LW", 0x00002003, "I"), ("LBU", 0x00004003, "I"), ("LHU", 0x00005003, "I"),
        ("SB", 0x00000023, "S"), ("SH", 0x00001023, "S"), ("SW", 0x00002023, "S"),
        ("ADDI", 0x00000013, "I"), ("SLTI", 0x00002013, "I"), ("SLTIU", 0x00003013, "I"),
        ("XORI", 0x00004013, "I"), ("ORI", 0x00006013, "I"), ("ANDI", 0x00007013, "I"),
        ("SLLI", 0x00001013, "I"), ("SRLI", 0x00005013, "I"), ("SRAI", 0x40005013, "I"),
        ("ADD", 0x00000033, "R"), ("SUB", 0x40000033, "R"), ("SLL", 0x00001033, "R"),
        ("SLT", 0x00002033, "R"), ("SLTU", 0x00003033, "R"), ("XOR", 0x00004033, "R"),
        ("SRL", 0x00005033, "R"), ("SRA", 0x40005033, "R"), ("OR", 0x00006033, "R"), ("AND", 0x00007033, "R")
      )

      var pcCounter = 0x1000

      for ((name, baseInst, instType) <- patterns) {

        val rs1List = Array.fill(p(FETCH_WIDTH))(rand.nextInt(32))
        val rs2List = Array.fill(p(FETCH_WIDTH))(rand.nextInt(32))
        val rdList  = Array.fill(p(FETCH_WIDTH))(rand.nextInt(32))
        val immList = Array.fill(p(FETCH_WIDTH))(BigInt(12, rand))

        val robIdList = Array.fill(p(FETCH_WIDTH)) { if (nextROBId.nonEmpty) nextROBId.dequeue() else 0 }
        val prfList = Array.fill(p(FETCH_WIDTH)) { if (freePRF.nonEmpty) freePRF.dequeue() else 0 }

        for (i <- 0 until p(FETCH_WIDTH)) {
          val inst: BigInt = instType match {
            case "R" => baseInst | (BigInt(rdList(i)) << 7) | (BigInt(rs1List(i)) << 15) | (BigInt(rs2List(i)) << 20)
            case "I" => baseInst | (BigInt(rdList(i)) << 7) | (BigInt(rs1List(i)) << 15) | ((immList(i) & 0xFFF) << 20)
            case "S" => baseInst | ((immList(i) & 0x1F) << 7) | (BigInt(rs1List(i)) << 15) | (BigInt(rs2List(i)) << 20) | (((immList(i) >> 5) & 0x7F) << 25)
            case "B" => baseInst | (((immList(i) >> 1) & 0xF) << 8) | (((immList(i) >> 11) & 0x1) << 7) | (BigInt(rs1List(i)) << 15) | (BigInt(rs2List(i)) << 20) | (((immList(i) >> 5) & 0x3F) << 25) | (((immList(i) >> 12) & 0x1) << 31)
            case "U" => baseInst | (BigInt(rdList(i)) << 7) | ((immList(i) & 0xFFFFF) << 12)
            case "J" => baseInst | (BigInt(rdList(i)) << 7) | ((immList(i) & 0xFF) << 21) | (((immList(i) >> 8) & 0x1) << 20) | (((immList(i) >> 9) & 0x3FF) << 12) | (((immList(i) >> 19) & 0x1) << 31)
          }

          dut.io.if5_pc_vec_i(i).poke((pcCounter + i*4).U)
          dut.io.if5_inst_i(i).poke(inst.U)
          dut.io.if5_instValid_i(i).poke(true.B)
        }

        dut.clock.step(pipeDepth)

        for (i <- 0 until p(FETCH_WIDTH)) {
          val uop = dut.io.alu_uops(i)
          val valid = uop.valid.peek().litToBoolean
          val rdVal = uop.rd.peek().litValue
          val rs1Val = uop.rs1.peek().litValue
          val rs2Val = uop.rs2.peek().litValue

          println(f"Instr $name%-5s PC=0x${pcCounter + i*4}%04X Valid=$valid RD=0x${rdVal.toInt}%02X RS1=0x${rs1Val.toInt}%02X RS2=0x${rs2Val.toInt}%02X ROB=${robIdList(i)} PRF=${prfList(i)}")

          assert(valid, s"$name instruction uOp should be valid at dispatch2")
        }

        pcCounter += 4
      }

      // flush & stall 测试
      dut.io.commit_redirect.flush.poke(true.B)
      dut.clock.step(1)
      dut.io.commit_redirect.flush.poke(false.B)
      dut.clock.step(pipeDepth)

      dut.io.stall_from_backpipe.poke(true.B)
      dut.clock.step(2)
      dut.io.stall_from_backpipe.poke(false.B)
      dut.clock.step(pipeDepth)
    }
  }
}