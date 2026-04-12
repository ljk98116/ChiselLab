package MyCPU.frontend.BPU

import chisel3._ 
import chisel3.util._ 
import MyCPU.config._ 
import MyCPU.io._ 
import MyCPU.utils._
import org.chipsalliance.cde.config._

class PredictorWbQueue(size: Int)(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val flush = Input(Bool())
        val enq = Input(Vec(p(FETCH_WIDTH), new PredictorUpdateInfo))
        val deq = Output(Vec(p(L1BTBParam).btbBankNum, new PredictorUpdateInfo))
        val enqReady = Output(Bool())
        val deqReady = Output(Vec(p(L1BTBParam).btbBankNum, Bool()))
    })

    val bankNum    = p(L1BTBParam).btbBankNum
    val bankBits   = log2Ceil(bankNum)
    val offsetBits = p(L1BTBParam).btbOffsetBits

    val skidBuffers = Seq.fill(bankNum)(
        Module(new MultiPortQueueNW1R(size / bankNum, p(FETCH_WIDTH), new PredictorUpdateInfo))
    )

    for(b <- 0 until bankNum) {
        val bankHits = (0 until p(FETCH_WIDTH)).map(
            index => io.enq(index).pc(bankBits + offsetBits - 1, offsetBits) === b.U && 
                io.enq(index).valid 
        )

        val hasBankHit = VecInit(bankHits).asUInt.orR
        val bankHitIndex = PriorityEncoder(bankHits)

        io.deq(b) := Mux(
            skidBuffers(b).io.deqReady, 
            skidBuffers(b).io.deq, 
            Mux(hasBankHit, io.enq(bankHitIndex), 0.U.asTypeOf(new PredictorUpdateInfo))
        )

        io.deqReady(b) := skidBuffers(b).io.deqReady || hasBankHit
        skidBuffers(b).io.deqValid := true.B

        // wb1 calculate the prefix sum, 
        val wb1_bankHitPrefixSum = WireInit(VecInit(
            Seq.fill(p(FETCH_WIDTH))(0.U(log2Ceil(p(FETCH_WIDTH)).W))
        ))

        wb1_bankHitPrefixSum(0) := 0.U
        for(i <- 1 until p(FETCH_WIDTH)) {
            wb1_bankHitPrefixSum(i) := wb1_bankHitPrefixSum(i-1) + bankHits(i-1) & (
                !hasBankHit || (hasBankHit && (i-1).U =/= bankHitIndex)
            )
        }

        val wb2_bankHitPrefixSum = RegNext(wb1_bankHitPrefixSum)
        val wb2_enq = RegNext(io.enq)

        for(i <- 0 until p(FETCH_WIDTH)) {
            skidBuffers(b).io.enq(i) := wb2_enq(wb2_bankHitPrefixSum(i))
            skidBuffers(b).io.enqValid(i) := wb2_enq(wb2_bankHitPrefixSum(i)).valid
        }

        skidBuffers(b).io.flush := io.flush
    }

    io.enqReady := Cat((0 until bankNum).map(
        b => skidBuffers(b).io.enqReady
    )).andR      
}