package MyCPU.test

import chisel3._
import chiseltest._
import org.scalatest.freespec.AnyFreeSpec
import scala.collection.mutable.{ArrayBuffer, Map => MMap, Stack => MStack}
import org.chipsalliance.cde.config._
import MyCPU.frontend._
import MyCPU.config._
import MyCPU.io._
import MyCPU.frontend.BPU.RAS

// 现在这个 trace 里 DUT 会真正执行到什么
// 段                  关键行为                                            期望
// main                顺序 call 11 个测试段 + 自旋 halt                    —
// ras_basic           同一函数调 2 次，各 1 个 return                     RAS 命中 ~100%
// ras_nested          call→call→ret→ret 的 LIFO                          RAS 命中 ~100%
// ras_deep            16 层嵌套压栈再逐层弹出                             若 RAS ≥16 命中高；否则深层错
// ras_branch_mix      call 前后夹条件分支                                分支预测不污染 RAS
// alt_tntn   @0x600   0x600 访问 18 次（9T+9N），0x604 访问 9 次          2-bit 计数器两端震荡，命中 ~50%
// periodic3  @0x700   0x700 访问 30 次（20T+10N），周期 TTN              GShare 需历史相关性
// sat        @0x800   0x800 访问 5 次（4T+1N）                            饱和计数器学习曲线
// hist_TN    @0x900   0x900 访问 9 次（8T+1N），0x910 永远 N              单向循环，GHR 进入周期
// alias_X/Y  @0xA00/E00 低 10 位相同，GShare 无 tag 时互相污染             别名实验
// long_loop  @0xB00   0xB00/0xB04 各 101 次（100T+1N）                    长循环退出

case class Instr(
  pc: Long,
  isCall:   Boolean = false,
  isReturn: Boolean = false,
  isBranch: Boolean = false,
  isCond:   Boolean = false,                    // 是否是条件分支（用于预解码/统计）
  taken:    Boolean = false,
  target:   Long    = 0,
  tag:      String  = "misc",
  takenFn:  Option[Int => Boolean] = None       // 若定义，按 visitCount 决定 taken
)

class FrontEndBranchPredTest extends AnyFreeSpec with ChiselScalatestTester {

  implicit val p: Parameters =
    new RV64BTBConfig ++ new BaseRV64IConfig ++
    new GSharePredictorConfig ++ new L1CacheConfig ++
    new UseSv32Config ++ new UseCustomLibraryConfig

  private val ENTRY = 0x80000000L

  private def buildTrace(): ArrayBuffer[Instr] = {
    val trace = new ArrayBuffer[Instr]()
    val base  = ENTRY

    def emit(i: Instr): Unit = trace += i
    def call(pc: Long, target: Long, tag: String) =
      emit(Instr(pc, isCall = true, isBranch = true, taken = true, target = target, tag = tag))
    def ret(pc: Long, tag: String) =
      emit(Instr(pc, isReturn = true, isBranch = true, taken = true, tag = tag))
    def nop(pc: Long, tag: String) =
      emit(Instr(pc, tag = tag))
    def cb(pc: Long, taken: Boolean, target: Long, tag: String) =
      emit(Instr(pc, isBranch = true, isCond = true, taken = taken, target = target, tag = tag))
    def cbFn(pc: Long, fn: Int => Boolean, target: Long, tag: String) =
      emit(Instr(pc, isBranch = true, isCond = true, target = target, tag = tag, takenFn = Some(fn)))
    def jump(pc: Long, target: Long, tag: String) =
      emit(Instr(pc, isBranch = true, isCond = false, taken = true, target = target, tag = tag))

    // ══════════════ main @ 0x000 ══════════════
    call(base + 0x000, base + 0x100, "main"); nop(base + 0x004, "main")
    call(base + 0x008, base + 0x200, "main"); nop(base + 0x00C, "main")
    call(base + 0x010, base + 0x300, "main"); nop(base + 0x014, "main")
    call(base + 0x018, base + 0x500, "main"); nop(base + 0x01C, "main")
    call(base + 0x020, base + 0x600, "main"); nop(base + 0x024, "main")
    call(base + 0x028, base + 0x700, "main"); nop(base + 0x02C, "main")
    call(base + 0x030, base + 0x800, "main"); nop(base + 0x034, "main")
    call(base + 0x038, base + 0x900, "main"); nop(base + 0x03C, "main")
    call(base + 0x040, base + 0xA00, "main"); nop(base + 0x044, "main")
    call(base + 0x048, base + 0xE00, "main"); nop(base + 0x04C, "main")
    call(base + 0x050, base + 0xB00, "main"); nop(base + 0x054, "main")
    jump(base + 0x058, base + 0x058, "main")   // 自旋（halt）

    // ══════════════ ras_basic @ 0x100 ══════════════
    // 同一个函数被调用两次，RAS 必须按 LIFO 正确返回两个不同地址
    call(base + 0x100, base + 0x140, "ras_basic"); nop(base + 0x104, "ras_basic")
    call(base + 0x108, base + 0x140, "ras_basic"); nop(base + 0x10C, "ras_basic")
    ret (base + 0x110, "ras_basic")
    nop (base + 0x140, "ras_basic")
    ret (base + 0x144, "ras_basic")

    // ══════════════ ras_nested @ 0x200 ══════════════
    // 嵌套 call → LIFO 必须严格
    call(base + 0x200, base + 0x240, "ras_nested"); nop(base + 0x204, "ras_nested")
    ret (base + 0x208, "ras_nested")
    call(base + 0x240, base + 0x280, "ras_nested"); nop(base + 0x244, "ras_nested")
    ret (base + 0x248, "ras_nested")
    nop (base + 0x280, "ras_nested")
    ret (base + 0x284, "ras_nested")

    // ══════════════ ras_deep @ 0x300 (16 层嵌套) ══════════════
    // 16 层 call 压栈（0x300 + 循环里的 15 次），再逐层 return
    call(base + 0x300, base + 0x340, "ras_deep"); nop(base + 0x304, "ras_deep")
    ret (base + 0x308, "ras_deep")
    for (i <- 1 until 16) {
      val fpc  = base + 0x340 + (i - 1) * 0x10
      val next = base + 0x340 + i * 0x10
      call(fpc, next, "ras_deep")
      nop (fpc + 4, "ras_deep")
      ret (fpc + 8, "ras_deep")
    }
    val fLast = base + 0x340 + 15 * 0x10    // 0x430
    nop(fLast,     "ras_deep")
    ret(fLast + 4, "ras_deep")

    // ══════════════ ras_branch_mix @ 0x500 ══════════════
    // 分支 + call/ret 混合
    cb  (base + 0x500, taken = true, base + 0x510, "ras_branch_mix")
    nop (base + 0x504, "ras_branch_mix")   // 死代码（taken 时被跳过）
    nop (base + 0x508, "ras_branch_mix")
    nop (base + 0x50C, "ras_branch_mix")
    call(base + 0x510, base + 0x540, "ras_branch_mix"); nop(base + 0x514, "ras_branch_mix")
    ret (base + 0x518, "ras_branch_mix")
    nop (base + 0x540, "ras_branch_mix")
    ret (base + 0x544, "ras_branch_mix")

    // ══════════════ alt_tntn @ 0x600 (同 PC 交替 T/N) ══════════════
    // PC 0x600 被反复访问：c 偶数 → T（跳回自身）；c 奇数 → N（落到 0x604）
    // 0x604 条件跳回 0x600（c < 8 时跳，c=8 时落穿到 0x608）
    // 期望：0x600 访问序列 T N T N … T N（共 18 次，9T+9N）
    //       0x604 访问序列 T T T T T T T T N（共 9 次，8T+1N）
    // 2-bit 计数器在 T/N 交替输入下无法稳定，命中率约 50%
    cbFn(base + 0x600, c => (c % 2 == 0), base + 0x600, "alt_tntn")
    cbFn(base + 0x604, c => c < 8,         base + 0x600, "alt_tntn")
    nop (base + 0x608, "alt_tntn")
    ret (base + 0x60C, "alt_tntn")

    // ══════════════ periodic3 @ 0x700 (同 PC 周期 3：TTN) ══════════════
    // 0x700 访问序列：T T N T T N T T N …（共 30 次，20T+10N）
    // 0x704 在 c=9 时落穿到 0x708
    cbFn(base + 0x700, c => (c % 3 != 2), base + 0x700, "periodic3")
    cbFn(base + 0x704, c => c < 9,         base + 0x700, "periodic3")
    nop (base + 0x708, "periodic3")
    ret (base + 0x70C, "periodic3")

    // ══════════════ sat @ 0x800 (4T 饱和后转 N) ══════════════
    // 0x800 访问序列：T T T T N  ← 2-bit 计数器饱和/转换（共 5 次，4T+1N）
    cbFn(base + 0x800, c => c < 4, base + 0x800, "sat")
    nop (base + 0x804, "sat")
    ret (base + 0x808, "sat")

    // ══════════════ hist @ 0x900 (A↔B 单向循环, 历史相关) ══════════════
    // A(0x900) 跳 B(0x910)；B 永远 N → fall through 到 0x914；0x914 无条件 jump 回 A
    // A 在 c=0..7 时 T，c=8 时 N → 落到 0x904，再 0x908 ret 出口
    // 期望：A 访问 9 次（8T+1N），B 访问 8 次（全 N），GHR 进入稳定周期
    cbFn(base + 0x900, c => c < 8, base + 0x910, "hist_TN")
    nop (base + 0x904, "hist_TN")   // A 的 N 落穿点（非死代码）
    ret (base + 0x908, "hist_TN")   // 出口

    // B @ 0x910: 永远 N → fall through 到 0x914
    cbFn(base + 0x910, _ => false, base + 0x900, "hist_TN")
    jump(base + 0x914, base + 0x900, "hist_TN")
    nop (base + 0x918, "hist_TN")

    // ══════════════ alias @ 0xA00 / 0xE00 (GShare 索引冲突) ══════════════
    // 0xA00 与 0xE00 低 10 位相同（0x200）→ GShare 无 tag 时必然互相污染
    cbFn(base + 0xA00, c => c < 4, base + 0xA00, "alias_X")
    nop (base + 0xA04, "alias_X")
    ret (base + 0xA08, "alias_X")
    cbFn(base + 0xE00, c => c < 4, base + 0xE00, "alias_Y")
    nop (base + 0xE04, "alias_Y")
    ret (base + 0xE08, "alias_Y")

    // ══════════════ long_loop @ 0xB00 (典型循环退出) ══════════════
    // 101 次迭代：T×100 + N×1
    nop (base + 0xB00, "long_loop")
    cbFn(base + 0xB04, c => c < 100, base + 0xB00, "long_loop")
    nop (base + 0xB08, "long_loop")
    ret (base + 0xB0C, "long_loop")

    trace
  }

  // ─────────────────────────────────────────────────────────
  //  静态自洽检查：按 trace 自身的语义模拟执行，不能跳到 trace 外
  // ─────────────────────────────────────────────────────────
  private def validateTrace(trace: ArrayBuffer[Instr], entry: Long): Unit = {
    val map = trace.map(i => i.pc -> i).toMap
    require(map.contains(entry), s"入口 0x${entry.toHexString} 不在 trace 中")

    var pc = entry
    val visited = MMap[Long, Int]().withDefaultValue(0)
    val ras     = MStack[Long]()
    var steps   = 0
    val MAX     = 5000

    while (steps < MAX) {
      map.get(pc) match {
        case None =>
          println(s"[validate] WARN: 第 $steps 步离开 trace：pc=0x${pc.toHexString}")
          return
        case Some(inst) =>
          val c = visited(pc)
          visited(pc) = c + 1
          val next =
            if (inst.isCall) { ras.push(pc + 4); inst.target }
            else if (inst.isReturn) {
              if (ras.nonEmpty) ras.pop() else pc + 4L
            } else if (inst.isBranch) {
              val t = inst.takenFn.map(_(c)).getOrElse(inst.taken)
              if (t) inst.target else pc + 4L
            } else pc + 4L

          // 真正的 halt：无条件跳转且目标就是自身（如 main 的 0x058 自旋）
          // 注意不要误伤 0x600 这类「条件跳回自身」的循环
          if (inst.isBranch && !inst.isCond && inst.target == pc && next == pc && c >= 1) {
            println(s"[validate] trace OK（在 0x${pc.toHexString} 自旋 halt），共执行 $steps 步")
            return
          }

          if (inst.isBranch && !map.contains(next)) {
            sys.error(s"[validate] 跳转目标不在 trace 中: pc=0x${pc.toHexString} -> 0x${next.toHexString}")
          }
          pc = next
          steps += 1
      }
    }
    println(s"[validate] trace OK，共执行 $steps 步")
  }

  // ─────────────────────────────────────────────────────────
  //  测试主体
  // ─────────────────────────────────────────────────────────
  "Frontend Branch Prediction (accurate assertions)" in {
    val trace = buildTrace()
    validateTrace(trace, ENTRY)

    val instrMap = trace.map(i => i.pc -> i).toMap
    def getInstr(pc: Long): Instr = instrMap.getOrElse(pc, Instr(pc, tag = "unknown"))

    test(new RAS) { dut =>
      // 初始栈空，rasTopPtr=0
      dut.io.redirectValid.poke(false.B)
      dut.io.if2_isCall.poke(false.B)
      dut.io.if2_isReturn.poke(false.B)
      dut.io.if2_pcValid.poke(false.B)
      dut.clock.step()

      // push 0x1004
      dut.io.if2_isCall.poke(true.B)
      dut.io.if2_pc.poke(0x1000L.U)
      dut.io.if2_pcValid.poke(true.B)
      dut.clock.step()
      println(s"after push: ptr=${dut.io.rasTopPtr.peek()}, top=0x${dut.io.rasTop.peek().litValue.longValue.toHexString}")
      // 期望：ptr=1

      dut.io.if2_isCall.poke(false.B)
      dut.io.if2_pcValid.poke(false.B)

      // push 0x2004
      dut.io.if2_isCall.poke(true.B)
      dut.io.if2_pc.poke(0x2000L.U)
      dut.io.if2_pcValid.poke(true.B)
      dut.clock.step()
      println(s"after push2: ptr=${dut.io.rasTopPtr.peek()}, top=0x${dut.io.rasTop.peek().litValue.longValue.toHexString}")
      // 期望：ptr=2, top=0x2004

      dut.io.if2_isCall.poke(false.B)
      dut.io.if2_pcValid.poke(false.B)

      // pop
      dut.io.if2_isReturn.poke(true.B)
      dut.io.if2_pcValid.poke(true.B)
      dut.clock.step()
      println(s"after pop: ptr=${dut.io.rasTopPtr.peek()}, top=0x${dut.io.rasTop.peek().litValue.longValue.toHexString}")
      // 期望：ptr=1, top=0x1004
    }

    test(new FrontEnd).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      val fetchWidth = p(FETCH_WIDTH)

      // ── Golden CPU（由 DUT 输出的 pc 驱动） ──
      class GoldenCPU {
        var ghr: Long = 0
        val ras        = MStack[Long]()
        val visitCount = MMap[Long, Int]().withDefaultValue(0)

        def process(pc: Long): (Boolean, Long, Boolean, Boolean, Boolean, String) = {
          val inst = getInstr(pc)
          val cnt  = visitCount(pc)
          visitCount(pc) = cnt + 1

          var taken  = inst.takenFn.map(_(cnt)).getOrElse(inst.taken)
          var target = if (taken) inst.target else pc + 4L

          if (inst.isCall) {
            ras.push(pc + 4L)
            taken  = true
            target = inst.target
          } else if (inst.isReturn) {
            if (ras.nonEmpty) {
              target = ras.pop()
              taken  = true
            } else {
              target = pc + 4L
              taken  = false
            }
          }

          if (inst.isBranch) {
            ghr = (ghr >> 1L) | (if (taken) (1L << 19) else 0L)
          }

          (taken, target, inst.isCall, inst.isReturn, inst.isBranch, inst.tag)
        }

        def GHR(): Long = ghr & 0xFFFFFL
      }

      val golden = new GoldenCPU()

      // ── 统计 ──
      var branchTotal, correctTotal        = 0
      var rasTotal,    rasCorrectTotal     = 0
      var condTotal,   condCorrect         = 0
      var btbAccessCount                   = 0
      val segStats = MMap[String, (Int, Int)]().withDefaultValue((0, 0))

      def recordSeg(tag: String, correct: Boolean): Unit = {
        val (t, c) = segStats(tag)
        segStats(tag) = (t + 1, c + (if (correct) 1 else 0))
      }

      var if0_pcVec   = IndexedSeq.fill(fetchWidth)(0.U(p(ADDR_WIDTH).W))
      var if0_pcValid = IndexedSeq.fill(fetchWidth)(false.B)

      var cycle     = 0
      val maxCycles = 3000

      while (cycle < maxCycles) {
        // ── 默认信号 ──
        dut.io.commit_redirect.flush.poke(false.B)
        dut.io.commit_redirect.flush_pc.poke(0.U)
        dut.io.commit_redirect.isCall.poke(false.B)
        dut.io.commit_redirect.isReturn.poke(false.B)
        dut.io.commit_redirect.pc.poke(0.U)
        dut.io.commit_redirect.rasTopPtr.poke(0.U)

        dut.io.icache_stall_req.poke(false.B)
        dut.io.stall_from_backpipe.poke(false.B)
        dut.io.if5_icache_refill_resp.valid.poke(false.B)
        dut.io.if5_icache_refill_resp.pa.bits.poke(0.U)
        for (i <- 0 until p(L1CacheLineLength))
          dut.io.if5_icache_refill_resp.data(i).poke(0.U)
        dut.io.if5_mshr_id.poke(0.U)
        dut.io.if5_mshr_id_valid.poke(false.B)
        dut.io.if2_icache_hit.poke(true.B)

        // ── if2 预解码 ──
        for (i <- 0 until fetchWidth) {
          if (dut.io.if2_valid_vec(i).peek().litToBoolean) {
            val pc   = dut.io.if2_pc_vec(i).peek().litValue.longValue
            val inst = getInstr(pc)
            val isUncondJump = inst.isBranch && !inst.isCall && !inst.isReturn && !inst.isCond

            dut.io.if2_predecode(i).isCall.poke(inst.isCall.B)
            dut.io.if2_predecode(i).isReturn.poke(inst.isReturn.B)
            dut.io.if2_predecode(i).isDirectJump.poke(inst.isCall.B)
            dut.io.if2_predecode(i).isIndirectJump.poke(inst.isReturn.B)
            dut.io.if2_predecode(i).isConditionalBranch.poke(inst.isCond.B)
            dut.io.if2_predecode(i).isUnconditionalBranch.poke(isUncondJump.B)
          } else {
            dut.io.if2_predecode(i).isCall.poke(false.B)
            dut.io.if2_predecode(i).isReturn.poke(false.B)
            dut.io.if2_predecode(i).isDirectJump.poke(false.B)
            dut.io.if2_predecode(i).isIndirectJump.poke(false.B)
            dut.io.if2_predecode(i).isConditionalBranch.poke(false.B)
            dut.io.if2_predecode(i).isUnconditionalBranch.poke(false.B)
          }
        }

        // ── ITLB 直通 ──
        for (i <- 0 until fetchWidth) {
          dut.io.if1_itlb_resp(i).hit.poke(true.B)
          dut.io.if1_itlb_resp(i).paddr.bits.poke(if0_pcVec(i).litValue.longValue.U)
          dut.io.if1_itlb_resp(i).valid.poke(if0_pcValid(i).litToBoolean.B)
        }

        for (i <- 0 until fetchWidth) dut.io.if2_insts(i).poke((1000 + i).U)

        // ── 读 if5 ──
        val if5_valids = dut.io.if5_instValid.peek()
        val if5_pcs    = dut.io.if5_pc_vec.peek()
        val if5_preds  = dut.io.if5_br_pred_info_vec.peek()

        // ── 清空 update 端口 ──
        for (i <- 0 until fetchWidth) {
          dut.io.wb1_predictor_update(i).valid.poke(false.B)
          dut.io.wb1_predictor_update(i).counter.poke(0.U)
          dut.io.wb1_predictor_update(i).ghist.poke(0.U)
          dut.io.wb1_predictor_update(i).lhist.poke(0.U)
          dut.io.wb1_predictor_update(i).misPred.poke(false.B)
          dut.io.wb1_predictor_update(i).pc.poke(0.U)
          dut.io.wb1_predictor_update(i).taken.poke(false.B)

          dut.io.wb1_l1_btb_update(i).valid.poke(false.B)
          dut.io.wb1_l1_btb_update(i).pc.poke(0.U)
          dut.io.wb1_l1_btb_update(i).isCall.poke(false.B)
          dut.io.wb1_l1_btb_update(i).isReturn.poke(false.B)
          dut.io.wb1_l1_btb_update(i).target.poke(0.U)
        }

        var commit_redirect = false

        for (i <- 0 until fetchWidth) {
          val if5_pc     = if5_pcs(i).litValue.longValue

          val predTarget = if5_preds(i).target.litValue.longValue
          val predTaken  = if5_preds(i).counter.litValue.longValue >= 2
          val rasTopPtr  = if5_preds(i).rasTopPtr.litValue.longValue
          val if5_valid = if5_valids(i).litToBoolean

          if(if5_valid) {
            assert(((if5_pc >> 2) & 3) == i, s"error at 0x%{$if5_pc}")
            val (realTaken, realTarget, realIsCall, realIsReturn, realIsBranch, tag) =
              golden.process(if5_pc)

            val misPred = (realTaken != predTaken) || 
                          (realTaken && realTarget != predTarget) ||
                          (getInstr(if5_pc).tag == "unknown")

            val inst = getInstr(if5_pc)

            if (realIsReturn) {
              rasTotal += 1
              if (!misPred) rasCorrectTotal += 1
            }
            if (realIsBranch && !realIsCall && !realIsReturn) {
              branchTotal += 1
              btbAccessCount += 1
              if (!misPred) correctTotal += 1
              if (inst.isCond) {
                condTotal += 1
                if (!misPred) condCorrect += 1
              }
            }
            recordSeg(tag, !misPred)

            // ── 反馈给 DUT ──
            dut.io.wb1_predictor_update(i).valid.poke(realIsBranch.B)
            dut.io.wb1_predictor_update(i).counter.poke(if5_preds(i).counter)
            dut.io.wb1_predictor_update(i).ghist.poke(if5_preds(i).ghist)
            dut.io.wb1_predictor_update(i).lhist.poke(if5_preds(i).lhist)
            dut.io.wb1_predictor_update(i).misPred.poke(misPred.B)
            dut.io.wb1_predictor_update(i).pc.poke(if5_pc.U)
            dut.io.wb1_predictor_update(i).taken.poke(realTaken.B)

            dut.io.wb1_l1_btb_update(i).valid.poke(realIsBranch.B)
            dut.io.wb1_l1_btb_update(i).pc.poke(if5_pc.U)
            dut.io.wb1_l1_btb_update(i).isCall.poke(realIsCall.B)
            dut.io.wb1_l1_btb_update(i).isReturn.poke(realIsReturn.B)
            dut.io.wb1_l1_btb_update(i).target.poke(realTarget.U)

            if (misPred && !commit_redirect) {
              dut.io.commit_redirect.flush.poke(true.B)
              dut.io.commit_redirect.flush_pc.poke(realTarget.U)
              dut.io.commit_redirect.isCall.poke(realIsCall.B)
              dut.io.commit_redirect.isReturn.poke(realIsReturn.B)
              dut.io.commit_redirect.pc.poke(if5_pc.U)
              dut.io.commit_redirect.rasTopPtr.poke(rasTopPtr.U)
              commit_redirect = true
            }
            if (branchTotal <= 5) {
              println(f"[pred] pc=0x$if5_pc%08x predT=$predTaken realT=$realTaken " +
                      f"predTgt=0x$predTarget%08x realTgt=0x$realTarget%08x " +
                      f"ctr=${if5_preds(i).counter.litValue.longValue} misPred=$misPred tag=$tag")
            }
          }
          else {
            // ── 反馈给 DUT ──
            dut.io.wb1_predictor_update(i).valid.poke(false.B)
            dut.io.wb1_predictor_update(i).counter.poke(if5_preds(i).counter)
            dut.io.wb1_predictor_update(i).ghist.poke(if5_preds(i).ghist)
            dut.io.wb1_predictor_update(i).lhist.poke(if5_preds(i).lhist)
            dut.io.wb1_predictor_update(i).misPred.poke(false.B)
            dut.io.wb1_predictor_update(i).pc.poke(if5_pc.U)
            dut.io.wb1_predictor_update(i).taken.poke(false.B)

            dut.io.wb1_l1_btb_update(i).valid.poke(false.B)
            dut.io.wb1_l1_btb_update(i).pc.poke(if5_pc.U)
            dut.io.wb1_l1_btb_update(i).isCall.poke(false.B)
            dut.io.wb1_l1_btb_update(i).isReturn.poke(false.B)
            dut.io.wb1_l1_btb_update(i).target.poke(0.U)            
          }
        }

        if (cycle % 500 == 0 && cycle > 0) {
          val acc = if (condTotal > 0) condCorrect.toDouble / condTotal * 100 else 0.0
          println(f"[cycle=$cycle] condAcc=${acc}%5.1f%%  condTotal=$condTotal rasTotal=$rasTotal")
          println(s"[cycle=$cycle] RAS push=${dut.io.pushCount.peek().litValue}, pop=${dut.io.popCount.peek().litValue}")
        }

        dut.io.refGHR.poke(golden.GHR().U)
        dut.clock.step(1)

        if0_pcVec   = dut.io.if0_itlb_req.peek().map(_.addr)
        if0_pcValid = dut.io.if0_itlb_req.peek().map(_.valid)

        cycle += 1
      }

      // ── 汇总 ──
      val overallAcc = if (branchTotal > 0) correctTotal.toDouble / branchTotal else 1.0
      val condAcc    = if (condTotal   > 0) condCorrect.toDouble   / condTotal   else 1.0
      val rasAcc     = if (rasTotal    > 0) rasCorrectTotal.toDouble / rasTotal  else 1.0

      println("=" * 70)
      println("最终统计：")
      println(f"  所有分支 : total=$branchTotal%4d correct=$correctTotal%4d acc=${overallAcc*100}%6.2f%%")
      println(f"  条件分支 : total=$condTotal%4d   correct=$condCorrect%4d   acc=${condAcc*100}%6.2f%%")
      println(f"  RAS 返回 : total=$rasTotal%4d    correct=$rasCorrectTotal%4d    acc=${rasAcc*100}%6.2f%%")
      println("  分段统计：")
      for ((tag, (t, c)) <- segStats.toSeq.sortBy(_._1)) {
        val a = if (t > 0) c.toDouble / t * 100 else 0.0
        println(f"    $tag%-20s total=$t%4d correct=$c%4d acc=$a%6.2f%%")
      }
      println("=" * 70)

      // ── 硬断言 ──
      assert(branchTotal >= 20, s"分支样本太少: $branchTotal")
      assert(rasTotal    >= 10, s"RAS 样本太少: $rasTotal")
      assert(overallAcc  >= 0.50, f"整体分支准确率 ${overallAcc*100}%.2f%% < 50%%")
      assert(rasAcc      >= 0.60, f"RAS 准确率 ${rasAcc*100}%.2f%% < 60%%")
    }
  }
}