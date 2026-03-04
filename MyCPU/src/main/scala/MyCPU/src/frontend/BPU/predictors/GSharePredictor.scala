package MyCPU.frontend.BPU.predictors

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._

import org.chipsalliance.cde.config._
import MyCPU.config._
import MyCPU.io._

// gshare predictor is accessed in if0 stage, update when commit
// if0 stage generate pht index, if1 get predict result
// update GHR and PHT
// use update info from PredictorWbQueue to update PHT
// update infos are assumed to be on different bank
// udpate GHR/PHT in 3 cycles
class GSharePredictor(implicit p: Parameters) extends Predictor {
    val fetchWidth = p(FETCH_WIDTH)
    val indexBits = p(GShareParamKey).BPUPredictorIndexBits
    val globalHistoryBits = p(GShareParamKey).BPUPredictorGlobalHistoryBits
    val counterBits = p(GShareParamKey).BPUPredictorCounterBits
    val offsetBits = 2

    val predictorSize = 1 << indexBits
    val phtBanks = Seq.fill(fetchWidth)(
        SyncReadMem(predictorSize / fetchWidth, UInt(counterBits.W))
    )

    var ghist = RegInit(0.U(globalHistoryBits.W))

    // predictor table
    // phtIndex (pc >> 2) ^ ghr(ghrWidth - 1, ghrWidth - indexBits)
    val if0_phtReqIndex = io.if0_req.map(
        req => (req.pc >> 2)(indexBits - 1, 0) ^ ghist(globalHistoryBits - 1, globalHistoryBits - indexBits)
    )
    val if0_phtReqIndexBankIds = io.if0_req.map(
        req => req.pc(log2Ceil(fetchWidth) - 1, 0) 
    )
    val if0_phtReqIndexBankRow = if0_phtReqIndex.map(
        phtReqIndex => phtReqIndex(indexBits - 1, log2Ceil(fetchWidth))
    )
    val if0_phtAccess = io.if0_req.map(_.valid)

    // if1 stage output predict result
    val if1_phtAccess = RegInit(VecInit(
        Seq.fill(fetchWidth)(false.B)
    ))

    // output is the counter value, which will be used to generate prediction result at if1 stage
    val phtReadData = WireInit(VecInit(
        Seq.fill(fetchWidth)(0.U(counterBits.W))
    ))

    phtReadData := (0 until fetchWidth).map(
        index => phtBanks(index).read(if0_phtReqIndexBankRow(index))
    )

    if1_phtAccess := if0_phtAccess

    for(i <- 0 until fetchWidth) {
        io.if0_predict(i) := 0.U.asTypeOf(new PredictorRespInfo)

        io.if1_predict(i).counter := phtReadData(i)
        io.if1_predict(i).valid := if1_phtAccess(i)
        io.if1_predict(i).ghist := ghist
        io.if1_predict(i).lhist := 0.U
    }

    val wb2_update = RegNext(io.update)
    val wb3_update = RegNext(wb2_update)
    // update PHT
    for(i <- 0 until fetchWidth) {
        val wb2_bankId = wb2_update(i).pc(log2Ceil(fetchWidth) + offsetBits - 1, offsetBits)
        val wb2_updatePhtIndex = (wb2_update(i).pc >> 2) ^ wb2_update(i).ghist(globalHistoryBits - 1, globalHistoryBits - indexBits) 
        val wb2_bankRow = wb2_updatePhtIndex(indexBits - 1, log2Ceil(fetchWidth))
        val wb2_oldPhtValue = wb2_update(i).counter
        val wb2_hitVec = VecInit((0 until fetchWidth).map(
            index => (index.U === wb2_bankId) && wb2_update(index).valid
        ))

        val wb2_nextResultIfTaken = WireInit(0.U(counterBits.W))
        val wb2_nextResultIfNotTaken = WireInit(0.U(counterBits.W))
        wb2_nextResultIfTaken := Mux(wb2_oldPhtValue === "b11".U, wb2_oldPhtValue, wb2_oldPhtValue + 1.U)
        wb2_nextResultIfNotTaken := Mux(wb2_oldPhtValue === "b00".U, wb2_oldPhtValue, wb2_oldPhtValue - 1.U)

        val wb3_hitVec = RegNext(wb2_hitVec)
        val wb3_bankRow = RegNext(wb2_bankRow)
        val wb3_oldPhtValue = RegNext(wb2_oldPhtValue)
        val wb3_nextResultIfTaken = RegNext(wb2_nextResultIfTaken)
        val wb3_nextResultIfNotTaken = RegNext(wb2_nextResultIfNotTaken)

        val wb3_nextPhtResult = WireInit(0.U(counterBits.W))
        wb3_nextPhtResult := Mux(wb3_update(i).taken, wb3_nextResultIfTaken, wb3_nextResultIfNotTaken)

        val wb4_nextPhtResult = RegNext(wb3_nextPhtResult)
        val wb4_bankRow = RegNext(wb3_bankRow)
        val wb4_hitVec = RegNext(wb3_hitVec)

        (0 until fetchWidth).map(
            index => {
                when(wb4_hitVec(index)) {
                    phtBanks(index).write(wb4_bankRow, wb4_nextPhtResult)
                }
            }
        )
    }

    // update GHR
    val wb2_num_branch = PopCount(wb2_update.map(_.valid))
    val wb2_first_taken = PriorityEncoder(wb2_update.map(_.taken))
    val wb2_taken = VecInit(wb2_update.map(_.taken))

    val wb3_num_branch = RegNext(wb2_num_branch)
    val wb3_first_taken = RegNext(wb2_first_taken)
    val wb3_taken = RegNext(wb2_taken)

    val wb3_ghistLeft = ghist << wb3_num_branch
    val wb3_ghistRight = wb3_taken.asUInt >> wb3_first_taken

    val wb4_ghistLeft = RegNext(wb3_ghistLeft)
    val wb4_ghistRight = RegNext(wb3_ghistRight)

    ghist := wb4_ghistLeft | wb4_ghistRight
}