// package MyCPU.test

// import chisel3._
// import chiseltest._
// import org.scalatest.freespec.AnyFreeSpec
// import scala.collection.mutable.{Queue, ArrayBuffer}
// import scala.util.control.Breaks._

// import org.chipsalliance.cde.config._
// import MyCPU.frontend._
// import MyCPU.config._
// import MyCPU.io._
// import chisel3.util.PopCount

// case class Instr(
//   pc: Long,
//   isCall: Boolean = false,
//   isReturn: Boolean = false,
//   isBranch: Boolean = false,
//   taken: Boolean = false,
//   target: Long = 0
// )

// class Meta(
//   isCall: Boolean = false,
//   isReturn: Boolean = false,
//   isDirectJump: Boolean = false,
//   isIndirectJump: Boolean = false,
//   isConditionalBranch: Boolean = false,
//   isUnconditionalBranch: Boolean = false
// ) {
//   def isCall(): Boolean = isCall
//   def isReturn(): Boolean = isReturn
//   def isDirectJump(): Boolean = isDirectJump
//   def isIndirectJump(): Boolean = isIndirectJump
//   def isConditionalBranch(): Boolean = isConditionalBranch
//   def isUnconditionalBranch(): Boolean = isUnconditionalBranch
// }

// class FrontEndBranchPredTest extends AnyFreeSpec with ChiselScalatestTester {

//   implicit val p: Parameters =
//     new BTBConfig ++ new BaseConfig ++
//     new GSharePredictorConfig ++ new L1CacheConfig ++
//     new UseSv32Config ++ new UseCustomLibraryConfig

//   "Frontend Branch Prediction (Correct Redirect Model)" in {

//     test(new FrontEnd).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

//       val fetchWidth = p(FETCH_WIDTH)

//       // ---------- 指令序列生成（同前） ----------
//       val trace = new ArrayBuffer[Instr]()
//       val base = 0x80000000L

//       def addCall(pc: Long, target: Long): Unit = trace += Instr(pc, true, false, true, true, target)
//       def addReturn(pc: Long): Unit = trace += Instr(pc, false, true, true, true)
//       def addCondBranch(pc: Long, taken: Boolean, target: Long): Unit = trace += Instr(pc, false, false, true, taken, target)
//       def addNop(pc: Long): Unit = trace += Instr(pc)

//       // RAS 测试
//       addCall(base + 0x00, base + 0x100)
//       addNop(base + 0x04)
//       addReturn(base + 0x08)

//       addCall(base + 0x10, base + 0x200)
//       addNop(base + 0x14)
//       addCall(base + 0x18, base + 0x300)
//       addNop(base + 0x1C)
//       addReturn(base + 0x20)
//       addReturn(base + 0x24)

//       for (i <- 0 until 16) {
//         addCall(base + 0x30 + i*4, base + 0x400 + i*4)
//       }
//       for (i <- 0 until 16) {
//         addReturn(base + 0x70 + i*4)
//       }

//       addCall(base + 0xB0, base + 0x500)
//       addNop(base + 0xB4)
//       addCondBranch(base + 0xB8, false, base + 0xB8 + 8)
//       addReturn(base + 0xBC)

//       // GShare 测试
//       // ---------- 在 trace 生成部分，将 GShare 测试重复 4 次 ----------
//       val repeat = 4
//       for (_ <- 0 until repeat) {
//         // 交替模式（T N T N）
//         for (i <- 0 until 8) {
//           addCondBranch(base + 0xC0 + i*4, i % 2 == 0, base + 0xC0 + i*4 + 8)
//         }

//         // 周期 3 模式（T T N）
//         for (i <- 0 until 9) {
//           addCondBranch(base + 0xE0 + i*4, i % 3 != 2, base + 0xE0 + i*4 + 8)
//         }

//         // 饱和计数器学习（连续 4 次 taken → 连续 4 次 not taken）
//         for (i <- 0 until 4) addCondBranch(base + 0x100 + i*4, true, base + 0x100 + i*4 + 8)
//         for (i <- 0 until 4) addCondBranch(base + 0x110 + i*4, false, base + 0x110 + i*4 + 8)

//         // 历史相关性：branch2 的方向依赖于 branch1
//         addCondBranch(base + 0x120, true, base + 0x120 + 8)
//         addCondBranch(base + 0x124, false, base + 0x124 + 8)
//         addCondBranch(base + 0x128, true, base + 0x128 + 8)
//         addCondBranch(base + 0x12C, true, base + 0x12C + 8)
//       }

//       // 综合场景
//       addCondBranch(base + 0x200, true, base + 0x300)
//       addCall(base + 0x300, base + 0x400)
//       addReturn(base + 0x304)
//       addReturn(base + 0x308)

//       for (i <- 0 until 32) {
//         if (i % 5 == 0) addCondBranch(base + 0x500 + i*4, true, base + 0x510)
//         else addNop(base + 0x500 + i*4)
//       }

//       // 地址到指令的映射
//       val instrMap = trace.map(instr => instr.pc -> instr).toMap

//       def getInstr(pc: Long): Instr = {
//         instrMap.getOrElse(pc, Instr(pc))
//       }

//       def predecoder(pc: Long): Meta = {
//         val inst = getInstr(pc)
//         new Meta(inst.isCall, inst.isReturn, false, false,
//           !(inst.isCall || inst.isReturn), inst.isCall || inst.isReturn)
//       }

//       // ---------- 黄金模型（由 DUT 的 pc 驱动） ----------
//       class GoldenCPU {
//         var ghr: Long = 0
//         var goldenRAS = scala.collection.mutable.Stack[Long]()

//         // 根据 pc 处理指令，返回 (taken, target, isCall, isReturn, isBranch)，并更新内部状态
//         def process(pc: Long): (Boolean, Long, Boolean, Boolean, Boolean) = {
//           val inst = getInstr(pc)
//           val isCall = inst.isCall
//           val isReturn = inst.isReturn
//           val isBranch = inst.isBranch
//           var taken = inst.taken
//           var target = inst.target

//           if (isCall) {
//             goldenRAS.push(pc + 4L)
//             println(s"[RAS] push $pc+4 -> 0x${(pc+4).toHexString}, stack size=${goldenRAS.size}")
//           } else if (isReturn) {
//             if (goldenRAS.nonEmpty) {
//               val top = goldenRAS.top
//               goldenRAS.pop()
//               println(s"[RAS] pop 0x${top.toHexString}, stack size=${goldenRAS.size}")
//               target = top
//             }
//           }

//           if (isCall) {
//             goldenRAS.push(pc + 4L)
//             taken = true
//             target = inst.target
//           } else if (isReturn) {
//             if (goldenRAS.nonEmpty) {
//               target = goldenRAS.top
//               goldenRAS.pop()
//               taken = true
//             } else {
//               target = pc + 4L
//               taken = false
//             }
//           } else if (isBranch) {
//             if (taken) {
//               target = inst.target
//             } else {
//               target = pc + 4L
//             }
//           } else {
//             taken = false
//             target = pc + 4L
//           }

//           if (isBranch) {
//             ghr = (ghr >> 1L) | (if (taken) (1L << 19) else 0L)
//           }

//           (taken, target, isCall, isReturn, isBranch)
//         }

//         def GHR(): Long = ghr & 0xFFFFF
//       }

//       // ---------- 测试循环 ----------
//       var cycle = 0
//       var branchTotal = 0
//       var correctTotal = 0
//       var rasTotal = 0
//       var rasCorrectTotal = 0
//       var btbAccessCount = 0

//       val goldencpu = new GoldenCPU()   // 不再需要初始 pc

//       var if0_pcVec = IndexedSeq.fill(fetchWidth)(0.U(p(ADDR_WIDTH).W))
//       var if0_pcValid = IndexedSeq.fill(fetchWidth)(false.B)

//       while (cycle < 1501) {
//         // 默认无 flush
//         var commit_redirect = false
//         dut.io.commit_redirect.flush.poke(false.B)
//         dut.io.commit_redirect.flush_pc.poke(0.U)
//         dut.io.commit_redirect.isCall.poke(false.B)
//         dut.io.commit_redirect.isReturn.poke(false.B)
//         dut.io.commit_redirect.pc.poke(0.U)
//         dut.io.commit_redirect.rasTopPtr.poke(0.U)

//         dut.io.icache_stall_req.poke(false.B)
//         dut.io.stall_from_backpipe.poke(false.B)
//         dut.io.icache_refill_valid.poke(false.B)
//         dut.io.if2_icache_hit.poke(true.B)

//         // if2 预解码驱动
//         for (i <- 0 until fetchWidth) {
//           if (dut.io.if2_valid_vec(i).peek().litToBoolean) {
//             val if2_predecodeRes = predecoder(dut.io.if2_pc_vec(i).peek().litValue.longValue)
//             dut.io.if2_predecode(i).isCall.poke(if2_predecodeRes.isCall().B)
//             dut.io.if2_predecode(i).isReturn.poke(if2_predecodeRes.isReturn().B)
//             dut.io.if2_predecode(i).isDirectJump.poke(if2_predecodeRes.isCall().B)
//             dut.io.if2_predecode(i).isIndirectJump.poke(if2_predecodeRes.isIndirectJump().B)
//             dut.io.if2_predecode(i).isConditionalBranch.poke(if2_predecodeRes.isConditionalBranch().B)
//             dut.io.if2_predecode(i).isUnconditionalBranch.poke(if2_predecodeRes.isUnconditionalBranch().B)
//           } else {
//             dut.io.if2_predecode(i).isCall.poke(false.B)
//             dut.io.if2_predecode(i).isReturn.poke(false.B)
//             dut.io.if2_predecode(i).isDirectJump.poke(false.B)
//             dut.io.if2_predecode(i).isIndirectJump.poke(false.B)
//             dut.io.if2_predecode(i).isConditionalBranch.poke(false.B)
//             dut.io.if2_predecode(i).isUnconditionalBranch.poke(false.B)
//           }
//         }

//         // ITLB 直通
//         for (i <- 0 until fetchWidth) {
//           dut.io.if1_itlb_resp(i).hit.poke(true.B)
//           dut.io.if1_itlb_resp(i).paddr.bits.poke(if0_pcVec(i).litValue.longValue.U)
//           dut.io.if1_itlb_resp(i).valid.poke(if0_pcValid(i).litToBoolean.B)
//         }

//         // ICache 数据填充
//         for (i <- 0 until p(L1CacheLineLength)) {
//           dut.io.if2_icacheline(i).poke((1000 + i).U)
//         }

//         val if5_valids = dut.io.if5_instValid.peek()
//         val if5_pcs = dut.io.if5_pc_vec.peek()
//         val if5_branchPreds = dut.io.if5_br_pred_info_vec.peek()

//         // 清空更新信号
//         for (i <- 0 until fetchWidth) {
//           dut.io.wb1_predictor_update(i).valid.poke(false.B)
//           dut.io.wb1_predictor_update(i).counter.poke(0.U)
//           dut.io.wb1_predictor_update(i).ghist.poke(0.U)
//           dut.io.wb1_predictor_update(i).lhist.poke(0.U)
//           dut.io.wb1_predictor_update(i).misPred.poke(0.B)
//           dut.io.wb1_predictor_update(i).pc.poke(0.U)
//           dut.io.wb1_predictor_update(i).taken.poke(false.B)

//           dut.io.wb1_l1_btb_update(i).valid.poke(false.B)
//           dut.io.wb1_l1_btb_update(i).pc.poke(0.U)
//           dut.io.wb1_l1_btb_update(i).isCall.poke(false.B)
//           dut.io.wb1_l1_btb_update(i).isReturn.poke(false.B)
//           dut.io.wb1_l1_btb_update(i).target.poke(0.U)
//         }

//         val validIndices = (0 until fetchWidth).filter(i => if5_valids(i).litToBoolean)

//         // 处理每个有效项
//         for (i <- validIndices) {
//           val if5_pc = if5_pcs(i).litValue.longValue
//           val if5_predTarget = if5_branchPreds(i).target.litValue.longValue
//           val if5_taken = if5_branchPreds(i).counter.litValue.longValue >= 2
//           val if5_isCall = if5_branchPreds(i).isCall.litToBoolean
//           val if5_isReturn = if5_branchPreds(i).isReturn.litToBoolean
//           val if5_rasTopPtr = if5_branchPreds(i).rasTopPtr.litValue.longValue

//           // 黄金模型处理该 pc，得到真实结果
//           val (realTaken, realTarget, realIsCall, realIsReturn, realIsBranch) = goldencpu.process(if5_pc)
//           // 判断 misPred
//           val misPred = if (realTaken != if5_taken) true
//                         else if (realTaken) realTarget != if5_predTarget
//                         else false

//           // 统计
//           if (realIsReturn) {
//             rasTotal += 1
//             if (!misPred) rasCorrectTotal += 1
//           }

//           if (realIsCall || realIsReturn || (realIsBranch && !realIsCall && !realIsReturn)) {
//             // 统计分支访问（包括 call/return）
//             btbAccessCount += 1
//           }

//           if (realIsBranch && !realIsCall && !realIsReturn) {
//             branchTotal += 1
//             if (!misPred) correctTotal += 1
//           }

//           // 更新 DUT
//           dut.io.wb1_predictor_update(i).valid.poke(realIsBranch.B)
//           dut.io.wb1_predictor_update(i).counter.poke(if5_branchPreds(i).counter)
//           dut.io.wb1_predictor_update(i).ghist.poke(if5_branchPreds(i).ghist)
//           dut.io.wb1_predictor_update(i).lhist.poke(if5_branchPreds(i).lhist)
//           dut.io.wb1_predictor_update(i).misPred.poke(misPred.B)
//           dut.io.wb1_predictor_update(i).pc.poke(if5_pc.U)
//           dut.io.wb1_predictor_update(i).taken.poke(realTaken.B)

//           dut.io.wb1_l1_btb_update(i).valid.poke(realIsBranch.B)
//           dut.io.wb1_l1_btb_update(i).pc.poke(if5_pc.U)
//           dut.io.wb1_l1_btb_update(i).isCall.poke(realIsCall.B)
//           dut.io.wb1_l1_btb_update(i).isReturn.poke(realIsReturn.B)
//           dut.io.wb1_l1_btb_update(i).target.poke(((realTarget - if5_pc) & 0xFF).U)

//           // 处理 flush
//           if (misPred && !commit_redirect) {
//             dut.io.commit_redirect.flush.poke(true.B)
//             dut.io.commit_redirect.flush_pc.poke(realTarget.U)
//             dut.io.commit_redirect.isCall.poke(realIsCall.B)
//             dut.io.commit_redirect.isReturn.poke(realIsReturn.B)
//             dut.io.commit_redirect.pc.poke(if5_pc.U)
//             dut.io.commit_redirect.rasTopPtr.poke(if5_rasTopPtr.U)
//             commit_redirect = true
//           }

//           // 在 for(i <- validIndices) 循环内，添加：
//           if (branchTotal < 50) {
//             println(f"PC=0x${if5_pc}%08x, pred_taken=$if5_taken, real_taken=$realTaken, " +
//                     f"pred_target=0x${if5_predTarget}%08x, real_target=0x${realTarget}%08x, " +
//                     f"counter=${if5_branchPreds(i).counter.litValue.longValue}, misPred=$misPred")
//           }
//         }

//         // 定期打印统计
//         if (cycle % 50 == 0 && cycle > 0) {
//           if (branchTotal > 0) {
//             val l0btbhitCount = dut.io.l0btbhitCount.peek().litValue.longValue
//             val l1btbhitCount = dut.io.l1btbhitCount.peek().litValue.longValue
//             val acc = correctTotal.toDouble / branchTotal * 100.0
//             val l0Rate = if (btbAccessCount > 0) l0btbhitCount.toDouble / btbAccessCount * 100 else 0.0
//             val l1Rate = if (btbAccessCount > 0) l1btbhitCount.toDouble / btbAccessCount * 100 else 0.0
//             println(s"cycle[$cycle]: Branch Pred Acc: ${acc}%, branchTotal:$branchTotal")
//             println(s"cycle[$cycle]: l0 btb hit rate: ${l0Rate}%, btbAccessCount:$btbAccessCount")
//             println(s"cycle[$cycle]: l1 btb hit rate: ${l1Rate}%, btbAccessCount:$btbAccessCount")
//             if (rasTotal > 0) {
//               val rasRate = rasCorrectTotal.toDouble / rasTotal * 100
//               println(s"cycle[$cycle]: ras correct rate: ${rasRate}%, rasTotal: $rasTotal")
//             } else {
//               println(s"cycle[$cycle]: no ras found")
//             }
//           } else {
//             println(s"cycle[$cycle]: no branch found")
//           }
//           println("")
//         }

//         // 驱动 GHR（用于调试）
//         dut.io.refGHR.poke(goldencpu.GHR().U)

//         // 时钟步进
//         dut.clock.step(1)

//         // 更新 if0 阶段信号
//         if0_pcVec = dut.io.if0_itlb_req.peek().map(_.addr)
//         if0_pcValid = dut.io.if0_itlb_req.peek().map(_.valid)

//         cycle += 1
//       }
//     }
//   }
// }