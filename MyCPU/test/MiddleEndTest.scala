package MyCPU.middleend

import chisel3._
import chiseltest._
import org.scalatest.freespec.AnyFreeSpec
import org.chipsalliance.cde.config._
import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

import scala.util.Random

class MiddleEndDecodeRenamePipelineTest extends AnyFreeSpec with ChiselScalatestTester {

  implicit val p: Parameters = (new BTBConfig ++ new BaseConfig ++
    new GSharePredictorConfig ++ new L1CacheConfig ++
    new UseSv32Config ++ new UseCustomLibraryConfig ++
    new StoreSetConfig).asInstanceOf[Parameters]

  "MiddleEnd decode and rename should handle all patterns correctly (pipeline aware)" in {

    test(new MiddleEnd).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

      // 初始化
      dut.io.stall_from_backpipe.poke(false.B)
      dut.io.commit_redirect.flush.poke(false.B)
      dut.io.commit_refill_write_back.valid.poke(false.B)
      dut.io.commit_spec_write_back.foreach(_.valid.poke(false.B))
      dut.io.commit_spec_robId_write_back.foreach(_.valid.poke(false.B))
      dut.io.loadRAWHarvard.valid.poke(false.B)

      val rand = new Random()

      val patterns: Seq[(String, BigInt)] = Seq(
        ("LUI", 0x00000037), ("AUIPC", 0x00000017), ("JAL", 0x0000006F),
        ("JALR", 0x00000067), ("BEQ", 0x00000063), ("BNE", 0x00001063),
        ("BLT", 0x00004063), ("BGE", 0x00005063), ("BLTU", 0x00006063),
        ("BGEU", 0x00007063), ("LB", 0x00000003), ("LH", 0x00001003),
        ("LW", 0x00002003), ("LBU", 0x00004003), ("LHU", 0x00005003),
        ("SB", 0x00000023), ("SH", 0x00001023), ("SW", 0x00002023),
        ("ADDI", 0x00000013), ("SLTI", 0x00002013), ("SLTIU", 0x00003013),
        ("XORI", 0x00004013), ("ORI", 0x00006013), ("ANDI", 0x00007013),
        ("SLLI", 0x00001013), ("SRLI", 0x00005013), ("SRAI", 0x40005013),
        ("ADD", 0x00000033), ("SUB", 0x40000033), ("SLL", 0x00001033),
        ("SLT", 0x00002033), ("SLTU", 0x00003033), ("XOR", 0x00004033),
        ("SRL", 0x00005033), ("SRA", 0x40005033), ("OR", 0x00006033), ("AND", 0x00007033)
      )

      var pcCounter = 0x1000

      for ((name, baseInst) <- patterns) {

        val rs1List = Array.fill(p(FETCH_WIDTH))(rand.nextInt(32))
        val rs2List = Array.fill(p(FETCH_WIDTH))(rand.nextInt(32))
        val rdList  = Array.fill(p(FETCH_WIDTH))(rand.nextInt(32))

        // ----------------------
        // Step 1: Poke FETCH stage
        // ----------------------
        for (i <- 0 until p(FETCH_WIDTH)) {
          val inst = (baseInst | (rs1List(i) << 15) | (rs2List(i) << 20) | (rdList(i) << 7)).U
          dut.io.if5_pc_vec_i(i).poke((pcCounter + i*4).U)
          dut.io.if5_inst_i(i).poke(inst)
          dut.io.if5_instValid_i(i).poke(true.B)
        }

        // ----------------------
        // Step 2: Clock pipeline until decode/rename valid
        // ----------------------
        var stable = false
        var maxCycle = 10 // 最多等10个cycle
        while (!stable && maxCycle > 0) {
          dut.clock.step(1)
          stable = true
          for (i <- 0 until p(FETCH_WIDTH)) {
            val uop = dut.io.alu_uops(i)
            if (!uop.valid.peek().litToBoolean) stable = false
          }
          maxCycle -= 1
        }

        assert(stable, s"$name uOps did not become valid in time")

        // ----------------------
        // Step 3: 校验 rename 输出
        // ----------------------
        for (i <- 0 until p(FETCH_WIDTH)) {
          val uop = dut.io.alu_uops(i)
          val valid = uop.valid.peek().litToBoolean
          val rd = uop.rd.peek().litValue
          val rs1 = uop.rs1.peek().litValue
          val rs2 = uop.rs2.peek().litValue

          println(f"Instr $name%-5s PC=0x${(pcCounter+i*4)}%04X Valid=$valid RD=$rd RS1=$rs1 RS2=$rs2")

          assert(valid, s"$name instruction uOp should be valid")
          assert(rd == rdList(i), s"$name RD mismatch: expected ${rdList(i)}, got $rd")
          assert(rs1 == rs1List(i), s"$name RS1 mismatch: expected ${rs1List(i)}, got $rs1")
          assert(rs2 == rs2List(i), s"$name RS2 mismatch: expected ${rs2List(i)}, got $rs2")
        }

        pcCounter += 4
      }

      // ----------------------
      // Step 4: 测试 flush
      // ----------------------
      dut.io.commit_redirect.flush.poke(true.B)
      dut.clock.step(1)
      dut.io.commit_redirect.flush.poke(false.B)

      // ----------------------
      // Step 5: 测试 stall
      // ----------------------
      dut.io.stall_from_backpipe.poke(true.B)
      dut.clock.step(2)
      dut.io.stall_from_backpipe.poke(false.B)
      dut.clock.step(1)
    }
  }
}