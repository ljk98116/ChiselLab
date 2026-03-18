package MyCPU.test

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

import scala.collection.mutable
import scala.util.Random

import MyCPU.frontend._
import MyCPU.config._
import org.chipsalliance.cde.config._

class FrontEndBranchPredTest extends AnyFlatSpec with ChiselScalatestTester {

  behavior of "FrontEnd Branch Prediction (Strict Clock Correct)"

  it should "work with true posedge semantics" in {

    implicit val p: Parameters =
      new BTBConfig ++ new BaseConfig ++
      new GSharePredictorConfig ++ new L1CacheConfig ++
      new UseSv32Config ++
      new UseCustomLibraryConfig

    test(new FrontEnd).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

      val rand = new Random(0)

      case class Instr(
        pc: Long,
        isBranch: Boolean = false,
        taken: Boolean = false,
        target: Long = 0,
        isCall: Boolean = false,
        isRet: Boolean = false
      )

      val trace = (0 until 1024).map { i =>
        val pc = i * 4L
        val r = rand.nextInt(10)

        if (r < 2) Instr(pc, isBranch = true, taken = true, target = pc + 16)
        else if (r == 3) Instr(pc, isCall = true, taken = true, target = pc + 64)
        else if (r == 4) Instr(pc, isRet = true, taken = true)
        else Instr(pc)
      }

      val traceMap = trace.map(x => x.pc -> x).toMap

      val ras = mutable.Stack[Long]()

      def goldenNext(inst: Instr): Long = {
        if (inst.isCall) inst.target
        else if (inst.isRet && ras.nonEmpty) ras.top
        else if (inst.isBranch && inst.taken) inst.target
        else inst.pc + 4
      }

      def updateRAS(inst: Instr): Unit = {
        if (inst.isCall) ras.push(inst.pc + 4)
        if (inst.isRet && ras.nonEmpty) ras.pop()
      }

      val seenPC = mutable.Set[Long]()

      var totalBranches = 0
      var correctPreds = 0

      // ============================
      // 延迟一拍控制信号（核心）
      // ============================
      var nextFlushValid = false
      var nextFlushPC = 0L
      var nextIsCall = false
      var nextIsRet = false
      var flushWindow = 0
      val PIPELINE_DEPTH = 6

      var cycle = 0

      dut.reset.poke(true.B)
      dut.clock.step(5)
      dut.reset.poke(false.B)

      // 🔥 warmup pipeline（关键）
      dut.clock.step(10)

      while (cycle < 1000) {

        val warmupCycles = 20

        val stall =
          if (cycle < warmupCycles) false
          else rand.nextInt(5) == 0

        val icacheMiss   = false
        
        dut.io.icache_stall_req.poke(stall.B)
        dut.io.if2_icache_hit.poke((!icacheMiss).B)

        // 默认无 redirect
        dut.io.commit_redirect.flush.poke(nextFlushValid.B)

        if (!nextFlushValid) {
          dut.io.commit_redirect.flush_pc.poke(0.U)
        }

        nextFlushValid = false

        dut.clock.step()

        // ============================
        // 3️⃣ flush window 递减
        // ============================
        if (flushWindow > 0) {
          flushWindow -= 1
        }

        // ============================
        // 4️⃣ 检查输出
        // ============================

        for (i <- 0 until p(FETCH_WIDTH)) {

          val valid = dut.io.if5_instValid(i).peek().litToBoolean

          // 🔥 关键：flush window 内完全忽略输出
          if (valid && flushWindow == 0) {

            val pc = dut.io.if5_pc_vec(i).peek().litValue.toLong
            print(s"[cycle=$cycle] PC 0x${pc.toHexString}\n")
            
            assert(!seenPC.contains(pc),
              s"[cycle=$cycle] DUP PC 0x${pc.toHexString}"
            )
            seenPC += pc

            assert(traceMap.contains(pc),
              s"[cycle=$cycle] UNKNOWN PC 0x${pc.toHexString}"
            )

            val inst = traceMap(pc)

            val isControl =
              inst.isBranch || inst.isCall || inst.isRet

            val predTarget =
              dut.io.if5_br_pred_info_vec(i).target.peek().litValue.toLong

            val expected = goldenNext(inst)

            val mispred = predTarget != expected
            val correct = predTarget == expected
            
            if (mispred) {

              nextFlushValid = true
              nextFlushPC = expected
              nextIsCall = inst.isCall
              nextIsRet = inst.isRet

              flushWindow = PIPELINE_DEPTH   // 🔥 核心修复
              print(s"[cycle=$cycle] mispred !! PC 0x${pc.toHexString}, target:0x${inst.target}, isCall:${inst.isCall}, isRet:${inst.isRet}\n")
              seenPC.clear()
            }
            else {
              print(s"[cycle=$cycle] pred right !! PC 0x${pc.toHexString}, target:0x${inst.target}, isCall:${inst.isCall}, isRet:${inst.isRet}\n")
            }
            updateRAS(inst)
          }
        }

        cycle += 1
      }
    }
  }
}
