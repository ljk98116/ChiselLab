package MyCPU.frontend.BPU

import chisel3._ 
import chisel3.util._ 
import MyCPU.config._ 
import MyCPU.io._ 
import org.chipsalliance.cde.config._
 
// Multi Write/ Multi Read queue for L1 BTB update 
// multi bank write to regfile, combine read 
// write at commit stage 0, 1, 2, read fetch width's btb update info to update btb 
// enqReady is false when wb1 write not allowed
// 1716MHz on asic
class L1BTBWbQueue(size: Int)(implicit p: Parameters) extends Module {
    val io = IO(new Bundle{
        val enq = Input(Vec(p(FETCH_WIDTH), new L1BTBUpdateInfo))
        val deq = Output(Vec(p(FETCH_WIDTH), new L1BTBUpdateInfo))
        val enqReady = Output(Bool())
        val deqReady = Output(Bool()) // maybe useless
    })

    require(p(L1BTBParam).btbBankNum == p(FETCH_WIDTH), "l1 btb wbqueue bankNum must equal to fetch width")
    require(size % p(FETCH_WIDTH) == 0, "invalid l1 btb wbqueue size")

    val offsetBits = p(L1BTBParam).btbOffsetBits
    val ptrWidth = log2Ceil(size / p(FETCH_WIDTH))
    val perBankSize = size / p(FETCH_WIDTH)

    val bankNum = p(L1BTBParam).btbBankNum
    val _1stBankBits = log2Ceil(bankNum)

    val queue_banks = Seq.fill(bankNum)(
        SyncReadMem(size / bankNum, new L1BTBUpdateInfo)
    )

    val tails = RegInit(VecInit(
        Seq.fill(bankNum)(0.U(ptrWidth.W))
    ))

    val heads = RegInit(VecInit(
        Seq.fill(bankNum)(0.U(ptrWidth.W))
    ))

    val freeCounts = RegInit(VecInit(
        Seq.fill(bankNum)((size / bankNum).U((ptrWidth + 1).W))
    ))

    val wr_able = WireInit(false.B)

    // stage 0
    val bankSel = Seq.fill(bankNum)(
        WireInit(VecInit(
            Seq.fill(p(FETCH_WIDTH))(false.B)
        ))
    )

    val bankCount = Seq.fill(bankNum)(
        WireInit(0.U((log2Ceil(p(FETCH_WIDTH)) + 1).W))
    )

    val stagedBankPrefix = Seq.fill(bankNum)(
        RegInit(VecInit(
            Seq.fill(p(FETCH_WIDTH))(0.U(log2Ceil(p(FETCH_WIDTH)).W))
        ))
    )

    val stagedBankSelect = Seq.fill(bankNum)(
        RegInit(VecInit(
            Seq.fill(p(FETCH_WIDTH))(false.B)
        ))
    )

    val stagedBankCount = Seq.fill(bankNum)(
        RegInit(0.U((log2Ceil(p(FETCH_WIDTH)) + 1).W))
    )

    for(b <- 0 until bankNum) {
        for(i <- 0 until p(FETCH_WIDTH)) {
            val bankId = io.enq(i).pc(_1stBankBits + offsetBits - 1, offsetBits)
            bankSel(b)(i) := io.enq(i).valid && (bankId === b.U)
        }

        val prefix = WireInit(VecInit(
            Seq.fill(p(FETCH_WIDTH))(0.U(log2Ceil(p(FETCH_WIDTH)).W))
        ))

        prefix(0) := 0.U
        for(i <- 1 until p(FETCH_WIDTH)) {
            prefix(i) := prefix(i-1) + bankSel(b)(i-1)
        }

        bankCount(b) := PopCount(bankSel(b))

        stagedBankPrefix(b) := prefix
        stagedBankSelect(b) := bankSel(b)
        stagedBankCount(b) := bankCount(b)
    }

    // stage 1
    val bankData = Seq.fill(bankNum)(
        WireInit(VecInit(
            Seq.fill(p(FETCH_WIDTH))(0.U.asTypeOf(new L1BTBUpdateInfo))
        ))
    )

    val wrAbleVec = Wire(Vec(bankNum, Bool()))
    val stagedWrAble = RegInit(false.B)
    val stagedBankData = Seq.fill(bankNum)(
        RegInit(VecInit(
            Seq.fill(p(FETCH_WIDTH))(0.U.asTypeOf(new L1BTBUpdateInfo))
        ))
    )
    val stagedBankCount2 = Seq.fill(bankNum)(
        RegInit(0.U((log2Ceil(p(FETCH_WIDTH)) + 1).W))
    )
    val enq = RegNext(io.enq)
    for(b <- 0 until bankNum) {
        for(i <- 0 until p(FETCH_WIDTH)) {
            when(stagedBankSelect(b)(i)) {
                bankData(b)(stagedBankPrefix(b)(i)) := enq(i)
            }
        }
        wrAbleVec(b) := ~(freeCounts(b) < stagedBankCount(b))
        stagedBankData(b) := bankData(b)
        stagedBankCount2(b) := stagedBankCount(b)
    }

    stagedWrAble := wrAbleVec.asUInt.andR
    io.enqReady := wrAbleVec.asUInt.andR

    // stage 2
    for(b <- 0 until bankNum) {
        for(i <- 0 until p(FETCH_WIDTH)) {
            when(i.U < stagedBankCount2(b) && stagedWrAble) {
                queue_banks(b).write(tails(b) + i.U, stagedBankData(b)(i))
            }
        }
        io.deq(b) := queue_banks(b).read(heads(b))
        heads(b) := heads(b) + io.deq(b).valid
        tails(b) := tails(b) + Mux(stagedWrAble, stagedBankCount2(b), 0.U)
        freeCounts(b) := freeCounts(b) + io.deq(b).valid - stagedBankCount2(b)
    }

    io.deqReady := io.deq.map(_.valid).reduce(_||_)
}