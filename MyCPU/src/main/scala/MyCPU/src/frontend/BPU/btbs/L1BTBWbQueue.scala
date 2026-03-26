package MyCPU.frontend.BPU

import chisel3._ 
import chisel3.util._ 
import MyCPU.config._ 
import MyCPU.io._ 
import MyCPU.utils._
import org.chipsalliance.cde.config._

// use 4 commit rob item to update
// wb stage1: calculate multi bank write id, bank write enable
// wb stage2: get bank enq items, and write into multi bank queue
class L1BTBWbQueue(size: Int)(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val enq = Input(Vec(p(FETCH_WIDTH), new L1BTBUpdateInfo))
        val deq = Output(Vec(p(L1BTBParam).btbBankNum, new L1BTBUpdateInfo))
        val enqReady = Output(Bool())
        val deqReady = Output(Vec(p(L1BTBParam).btbBankNum, Bool()))
    })

    val bankNum    = p(L1BTBParam).btbBankNum
    val bankBits   = log2Ceil(bankNum)
    val offsetBits = p(L1BTBParam).btbOffsetBits

    // =========================
    // 1️⃣ bankIdx + valid
    // =========================
    val bankIdxVec = VecInit(io.enq.map(e => e.pc(bankBits + offsetBits - 1, offsetBits)))
    val validsVec  = VecInit(io.enq.map(_.valid))

    // =========================
    // 2️⃣ first entry
    // =========================
    val firstOH    = PriorityEncoderOH(validsVec)
    val firstValid = validsVec.asUInt.orR

    val firstData = Mux1H(firstOH, io.enq)
    val firstBank = Mux1H(firstOH, bankIdxVec)

    // =========================
    // 3️⃣ second entry（不同 bank）
    // =========================
    val secondMask = Wire(Vec(p(FETCH_WIDTH), Bool()))
    for (i <- 0 until p(FETCH_WIDTH)) {
        secondMask(i) := validsVec(i) && (bankIdxVec(i) =/= firstBank)
    }

    val secondOH    = PriorityEncoderOH(secondMask)
    val secondValid = secondMask.asUInt.orR

    val secondData = Mux1H(secondOH, io.enq)

    // =========================
    // 4️⃣ skid buffer（2-entry）
    // =========================
    val skidValid = RegInit(VecInit(Seq.fill(2)(false.B)))
    val skidData  = Reg(Vec(2, new L1BTBUpdateInfo))

    when(firstValid) {
        skidValid(0) := true.B
        skidData(0)  := firstData
    }

    when(secondValid) {
        skidValid(1) := true.B
        skidData(1)  := secondData
    }

    io.enqReady := !(skidValid(0) && skidValid(1))

    // =========================
    // 5️⃣ 单队列（1W1R）
    // =========================
    val queue = Module(new Queue(new L1BTBUpdateInfo, size))

    val use0 = skidValid(0)
    val use1 = skidValid(1) && !use0

    queue.io.enq.valid := use0 || use1
    queue.io.enq.bits  := Mux(use0, skidData(0), skidData(1))

    when(queue.io.enq.fire) {
        when(use0) {
            skidValid(0) := false.B
        }.elsewhen(use1) {
            skidValid(1) := false.B
        }
    }

    // =========================
    // 6️⃣ 出队 + bank 分发
    // =========================
    val head      = queue.io.deq.bits
    val headValid = queue.io.deq.valid
    val headBank  = head.pc(bankBits + offsetBits - 1, offsetBits)

    val bankHit = Wire(Vec(bankNum, Bool()))
    for (b <- 0 until bankNum) {
        bankHit(b) := headValid && (headBank === b.U)
    }

    val doDeq = bankHit.asUInt.orR
    queue.io.deq.ready := doDeq

    // =========================
    // 7️⃣ 输出
    // =========================
    for (b <- 0 until bankNum) {
        io.deq(b) := Mux(bankHit(b), head, 0.U.asTypeOf(new L1BTBUpdateInfo))
        io.deqReady(b) := bankHit(b)
    }
}
