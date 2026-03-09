package MyCPU.frontend.BPU

import chisel3._ 
import chisel3.util._ 
import MyCPU.config._ 
import MyCPU.io._ 
import MyCPU.utils._
import org.chipsalliance.cde.config._

// wb stage1: calculate rob item 's bank id, gen select vec
// wb stage2: generate prefix vec
// wb stage3: select deq rob item, enq dispatched rob items
// wb stage4: write into skid buffer
class PredictorWbQueue(size: Int)(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val enq = Input(Vec(p(FETCH_WIDTH), new PredictorUpdateInfo))
        val deq = Output(Vec(p(FETCH_WIDTH), new PredictorUpdateInfo))
        val enqReady = Output(Bool()) // wr_able
        val deqReady = Output(Vec(p(FETCH_WIDTH), Bool())) // maybe useless        
    })

    def compactPrefix(sel: Vec[Bool]): Vec[UInt] = {
        val n = sel.length
        val w = log2Ceil(n + 1)
        val result = Wire(Vec(n, UInt(w.W)))

        result(0) := 0.U
        for (i <- 1 until n) {
            result(i) := result(i-1) + sel(i-1).asUInt
        }
        result
    }    

    val bankNum = p(FETCH_WIDTH)
    val infoWidth = (new PredictorUpdateInfo).getWidth
    val _1stBankBits = log2Ceil(bankNum)
    val offsetBits = 2
    val perBankSize = size / p(FETCH_WIDTH)

    require(size % p(FETCH_WIDTH) == 0, "invalid predictor wbqueue size")

    val skidBuffers = Seq.fill(bankNum)(
        Module(new MultiPortQueueNW1R(perBankSize, bankNum, new PredictorUpdateInfo))
    )

    // wb1 calculate bankselect and prefix
    val wb1_bankSelectArray = WireInit(VecInit(
        Seq.fill(bankNum)(
            VecInit(Seq.fill(p(FETCH_WIDTH))(false.B))
        )
    ))

    for(b <- 0 until bankNum) {
        for(i <- 0 until p(FETCH_WIDTH)) {
            val bankId = io.enq(i).pc(_1stBankBits + offsetBits - 1, offsetBits)
            wb1_bankSelectArray(b)(i) := io.enq(i).valid && (bankId === b.U)
        }
    }

    val perBankNeedWrite = wb1_bankSelectArray.map(sel => sel.reduce(_||_))

    val bankReadyVec = skidBuffers.map(_.io.enqReady)

    io.enqReady := perBankNeedWrite.zip(bankReadyVec).map {
        case (need, ready) => !need || ready
    }.reduce(_ && _)

    val deqValidVec = skidBuffers.map(_.io.deqReady)
    io.deqReady := deqValidVec

    // wb2 use banksel to get prefix vec
    val wb2_bankPrefixArray = WireInit(VecInit(
        Seq.fill(bankNum)(
            VecInit(Seq.fill(p(FETCH_WIDTH))(0.U(log2Ceil(p(FETCH_WIDTH)).W)))
        )
    ))
    val wb2_enq = RegNext(io.enq)
    val wb2_bankSelectArray = RegNext(wb1_bankSelectArray)
    for(b <- 0 until bankNum) {
        val prefix = WireInit(VecInit(
            Seq.fill(p(FETCH_WIDTH))(0.U(log2Ceil(p(FETCH_WIDTH)).W))
        ))

        wb2_bankPrefixArray(b) := compactPrefix(wb2_bankSelectArray(b))
    }

    // wb3 use banksel and prefix vec to generate bankData
    val wb3_bankDataArray = WireInit(VecInit(
        Seq.fill(bankNum)(
            VecInit(Seq.fill(p(FETCH_WIDTH))(0.U.asTypeOf(new PredictorUpdateInfo)))
        )
    ))
    val wb3_enq = RegNext(wb2_enq)
    val wb3_bankDataValidArray = 
        WireInit(VecInit(
            Seq.fill(bankNum)(VecInit(
                Seq.fill(p(FETCH_WIDTH))(false.B)
            ))
        ))
    
    val wb3_bankPrefixArray = RegNext(wb2_bankPrefixArray)
    val wb3_bankSelectArray = RegNext(wb2_bankSelectArray)

    for(b <- 0 until bankNum) {
        for(slot <- 0 until p(FETCH_WIDTH)) {
            val hitVec = VecInit((0 until p(FETCH_WIDTH)).map { i =>
                wb3_bankSelectArray(b)(i) &&
                wb3_bankPrefixArray(b)(i) === slot.U
            })
            wb3_bankDataArray(b)(slot) := Mux1H(hitVec, wb3_enq)
        }
        wb3_bankDataValidArray(b) := wb3_bankDataArray(b).map(_.valid)
        io.deq(b) := skidBuffers(b).io.deq
    }

    // wb4 write into skid buffer
    val wb4_bankDataArray = RegNext(wb3_bankDataArray)
    val wb4_bankDataValidArray = RegNext(wb3_bankDataValidArray)

    for(b <- 0 until bankNum) {
        skidBuffers(b).io.enq := wb4_bankDataArray(b)
        skidBuffers(b).io.enqValid := wb4_bankDataValidArray(b)
    }        
}