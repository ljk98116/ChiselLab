package MyCPU.frontend.BPU.predictors

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._

import org.chipsalliance.cde.config._
import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

// gshare predictor is accessed in if0 stage, update when commit
// if0 stage generate pht index, if1 get predict result, but if1 no redirect, timing path not allowed
// update GHR and PHT
// use update info from PredictorWbQueue to update PHT
// update infos are assumed to be on different bank
// udpate GHR/PHT in 2 cycles
class GSharePredictor(implicit p: Parameters) extends Predictor {
    val fetchWidth = p(FETCH_WIDTH)
    val indexBits = p(GShareParamKey).BPUPredictorIndexBits // 10
    val globalHistoryBits = p(GShareParamKey).BPUPredictorGlobalHistoryBits
    val counterBits = p(GShareParamKey).BPUPredictorCounterBits
    val offsetBits = 2

    val predictorSize = 1 << indexBits
    // 256x2 => 256x34[Nangate45]
    val phtBanks = Seq.tabulate(fetchWidth)(
        index => {
            if(p(UseNangate45)) Module(new Nangate45Bank(predictorSize / fetchWidth, UInt(34.W), s"PHTBank_${index}"))
            else Module(new LargeBank(predictorSize / fetchWidth, UInt(counterBits.W)))
        }
    )

    var ghist_commit = RegInit(0.U(globalHistoryBits.W))
    var ghist_spec = RegInit(0.U(globalHistoryBits.W))
    val ghist_spec_pred = WireInit(0.U(globalHistoryBits.W))

    io.if0_ghist_pred := ghist_spec_pred
    io.if0_lhist_pred := 0.U

    val if1_ghist = PipeReg(ghist_spec, io.if1_stall, io.if1_flush | io.if2_flush | io.commit_flush)
    val if2_ghist = PipeReg(if1_ghist, io.if2_stall, io.if2_flush | io.commit_flush)
    val if1_ghist_pred = PipeReg(ghist_spec_pred, io.if1_stall, io.if1_flush | io.commit_flush)

    val if2_predict = PipeReg(
        Cat(io.if1_predict.map(_.counter(counterBits - 1))), io.if2_stall, io.if2_flush
    )
    // if1 predecode, get branch count
    val if2_branchCount = PopCount(io.if2_predecode.map(_.asUInt.orR))
    val if2_predecodeIsBranch = io.if2_predecode.map(_.asUInt.orR)

    // calculate ghr_spec update prefix at if1 stage
    val if1_ghr_spec_prefix = WireInit(VecInit(
        Seq.fill(fetchWidth)(0.U((p(FETCH_OFFSET) + 1).W))
    ))

    if1_ghr_spec_prefix(0) := 0.U
    for(i <- 1 until fetchWidth) {
        if1_ghr_spec_prefix(i) := if1_ghr_spec_prefix(i-1) + if2_predecodeIsBranch(i-1)
    }

    val if2_ghr_spec_prefix = PipeReg(if1_ghr_spec_prefix, io.if2_stall, io.if2_flush)

    // ---------- pack ----------
    val if2_packedTaken = WireInit(VecInit(
        Seq.fill(fetchWidth)(0.U(p(FETCH_WIDTH).W))
    ))

    if2_packedTaken(0) := if2_predict(0) << if2_ghr_spec_prefix(0)
    for(i <- 1 until fetchWidth) {
        if2_packedTaken(i) := if2_packedTaken(i-1) | (if2_predict(i) << if2_ghr_spec_prefix(i))
    } 
    // predictor table
    // phtIndex (pc >> 2) ^ ghr_spec(ghrWidth - 1, ghrWidth - indexBits)
    // speculative GHR -> speculative predict
    // GHRSpec1New =/= GHRSpec1Pred => flush pipeline & redirect to current if2 pc
    // usePred flag
    //        if0                    if1                                                              if2
    // pc0    ghrspec0(!usePred) (ghrspec0 << l0 btb hit num) | l0_btb hit reverse  (ghrspec0 << predecode branch num) | if1_predict(use ghespec0)
    //                               if0                                                 ||           if1
    // pc1                       ghrspec1Pred(usePred)                                 ghrspec1 =/= ghrspec1Pred ? redirect to pc1 : continue
    //                                                                                                                                                        if0
    // pc1(redirect)                                                                                                                                  ghrspec1(!usePred)
    // isBranch
    // l0btb hit
    // GHRSpec1Pred
    val if0_usePredGHR = RegInit(false.B)
    if0_usePredGHR := Mux(io.if1_redirect.redirect, false.B, ~if0_usePredGHR)

    val if1_usePredGHR = PipeReg(if0_usePredGHR, io.if1_stall, io.commit_flush | io.if1_flush | io.if2_flush)
    ghist_spec_pred := ghist_spec << PopCount(io.if1_l0_btb_hit)

    val if0_phtReqIndex = WireInit(VecInit(
        Seq.fill(p(FETCH_WIDTH))(0.U(indexBits.W))
    ))

    for(i <- 0 until p(FETCH_WIDTH)) {
        if0_phtReqIndex(i) := Mux(
            if0_usePredGHR,
            (io.if0_req(i).pc >> 2)(indexBits - 1, 0) ^ ghist_spec_pred(globalHistoryBits - 1, globalHistoryBits - indexBits),
            (io.if0_req(i).pc >> 2)(indexBits - 1, 0) ^ ghist_spec(globalHistoryBits - 1, globalHistoryBits - indexBits)
        )
    }

    val if0_phtReqIndexBankIds = if0_phtReqIndex.map(
        index => index(log2Ceil(fetchWidth) - 1, 0) 
    )

    val if0_phtReqIndexBankRow = VecInit(if0_phtReqIndex.map(
        index => index(indexBits - 1, log2Ceil(fetchWidth))
    ))

    val if0_phtAccess = io.if0_req.map(_.valid)

    // if1 stage output predict result
    val if1_phtAccess = RegInit(VecInit(
        Seq.fill(fetchWidth)(false.B)
    ))

    // output is the counter value, which will be used to generate prediction result at if1 stage
    val phtReadData = WireInit(VecInit(
        Seq.fill(fetchWidth)(0.U(counterBits.W))
    ))

    // init bank ios
    (0 until fetchWidth).map(
        index => {
            phtBanks(index).io.raddr := 0.U
            phtBanks(index).io.wen := false.B
            phtBanks(index).io.waddr := 0.U
            phtBanks(index).io.wdata := 0.U
            phtBanks(index).io.wmask := ~(0.U)
        }
    )

    for (b <- 0 until fetchWidth) {

        val hits = VecInit((0 until fetchWidth).map(i =>
            (if0_phtReqIndexBankIds(i) === b.U) && if0_phtAccess(i)
        ))

        val hasHit = hits.asUInt.orR
        val selIdx = PriorityEncoder(hits)

        when(hasHit) {
            phtBanks(b).io.raddr := if0_phtReqIndexBankRow(selIdx)
        }
    }

    for (i <- 0 until fetchWidth) {
        val bankId_r = RegNext(if0_phtReqIndexBankIds(i))
        val valid_r  = RegNext(if0_phtAccess(i))

        phtReadData(i) := Mux1H(
            (0 until fetchWidth).map(b =>
                (bankId_r === b.U && valid_r) -> phtBanks(b).io.rdata
            )
        )
    }

    if1_phtAccess := if0_phtAccess
 
    for(i <- 0 until fetchWidth) {
        io.if0_predict(i) := 0.U.asTypeOf(new PredictorRespInfo)

        io.if1_predict(i).ghist := Mux(if1_usePredGHR, if1_ghist_pred, if1_ghist)
        io.if1_predict(i).counter := phtReadData(i)
        io.if1_predict(i).valid := if1_phtAccess(i) 
        io.if1_predict(i).lhist := 0.U
    }

    // inst 0 see current ghr
    // inst 1 see current ghr + inst 0 pred

    io.if1_predict(0).ghist := if2_ghist

    for(i <- 1 until fetchWidth) {
        io.if1_predict(i).ghist :=
            (if2_ghist << if2_ghr_spec_prefix(i)) |
            if2_packedTaken(i)
    }


    val wb2_update = RegNext(io.update)
    val wb3_update = RegNext(wb2_update)

    io.if0_stall := false.B
    // update PHT
    // ----------- 先把所有 i 的信息整理成 Vec -----------

    val wb3_bankId_vec = Wire(Vec(fetchWidth, UInt(log2Ceil(fetchWidth).W)))
    val wb3_bankRow_vec = Wire(Vec(fetchWidth, UInt((indexBits - log2Ceil(fetchWidth)).W)))
    val wb3_nextPhtResult_vec = Wire(Vec(fetchWidth, UInt(counterBits.W)))
    val wb3_valid_vec = Wire(Vec(fetchWidth, Bool()))

    for(i <- 0 until fetchWidth) {

        val wb2_updatePhtIndex =
            (wb2_update(i).pc >> 2)(indexBits - 1, 0) ^
            wb2_update(i).ghist(globalHistoryBits - 1, globalHistoryBits - indexBits)

        val wb2_bankId  = wb2_updatePhtIndex(log2Ceil(fetchWidth) - 1, 0)
        val wb2_bankRow = wb2_updatePhtIndex(indexBits - 1, log2Ceil(fetchWidth))
        val wb2_oldPhtValue = wb2_update(i).counter

        val wb2_nextTaken =
            Mux(wb2_oldPhtValue === "b11".U, wb2_oldPhtValue, wb2_oldPhtValue + 1.U)
        val wb2_nextNotTaken =
            Mux(wb2_oldPhtValue === "b00".U, wb2_oldPhtValue, wb2_oldPhtValue - 1.U)

        val wb3_bankId   = RegNext(wb2_bankId)
        val wb3_bankRow  = RegNext(wb2_bankRow)
        val wb3_taken    = RegNext(wb2_update(i).taken)
        val wb3_valid    = RegNext(wb2_update(i).valid)

        val wb3_nextTaken    = RegNext(wb2_nextTaken)
        val wb3_nextNotTaken = RegNext(wb2_nextNotTaken)

        val wb3_next =
            Mux(wb3_taken, wb3_nextTaken, wb3_nextNotTaken)

        wb3_bankId_vec(i)         := wb3_bankId
        wb3_bankRow_vec(i)        := wb3_bankRow
        wb3_nextPhtResult_vec(i)  := wb3_next
        wb3_valid_vec(i)          := wb3_valid
    }


    // ----------- 按 bank 做仲裁（关键） -----------

    for (b <- 0 until fetchWidth) {

        val hits = VecInit((0 until fetchWidth).map(i =>
            wb3_valid_vec(i) && (wb3_bankId_vec(i) === b.U)
        ))

        val hasHit = hits.asUInt.orR
        val selIdx = PriorityEncoder(hits)

        when(hasHit) {
            if(p(UseNangate45)) {
                io.if0_stall := true.B
            }

            phtBanks(b).io.wen   := true.B
            phtBanks(b).io.waddr := wb3_bankRow_vec(selIdx)
            phtBanks(b).io.wdata := wb3_nextPhtResult_vec(selIdx)
        }
    }


    // update GHR
    // valid x x x x
    // taken x x x x
    val wb2_ghr_shift = PopCount(wb2_update.map(_.valid))
    // target loc -> origin array mapping

    val takenVec = Wire(Vec(fetchWidth, Bool()))
    val validVec = wb3_update.map(_.valid)

    for (i <- 0 until fetchWidth) {
        takenVec(i) := wb3_update(i).taken
    }

    // ---------- prefix sum ----------
    val wb2_prefix = Wire(Vec(fetchWidth, UInt(log2Ceil(fetchWidth + 1).W)))
    wb2_prefix(0) := 0.U
    for (i <- 1 until fetchWidth) {
        wb2_prefix(i) := wb2_prefix(i-1) + validVec(i-1)
    }

    val wb3_ghr_shift = RegNext(wb2_ghr_shift)
    val wb3_prefix = RegNext(wb2_prefix)

    // ---------- pack ----------
    val packedTaken = WireInit(VecInit(
        Seq.fill(fetchWidth)(0.U(p(FETCH_WIDTH).W))
    ))

    packedTaken(0) := takenVec(0) << wb3_prefix(0)
    for(i <- 1 until fetchWidth) {
        packedTaken(i) := packedTaken(i-1) | (takenVec(i) << wb3_prefix(i))
    }

    // ---------- GHR update ----------
    ghist_commit := (ghist_commit << wb3_ghr_shift) | packedTaken(fetchWidth - 1)

    val wb2_misPred = wb2_update.map(_.misPred)
    val wb2_misPredIdx = PriorityEncoder(wb2_misPred)
    val wb2_hasMisPred = Cat(wb2_misPred).orR
    
    val wb3_hasMisPred = RegNext(wb2_hasMisPred)
    val wb3_misPredIdx = RegNext(wb2_misPredIdx)

    // exception
    when(io.commit_flush && !wb3_hasMisPred) {
        ghist_spec := (ghist_commit << wb3_ghr_shift) | packedTaken(fetchWidth - 1)
    }
    // branch misPred
    .elsewhen(io.commit_flush && wb3_hasMisPred) {
        ghist_spec := Cat(wb3_update(wb3_misPredIdx).ghist(globalHistoryBits - 2, 0), wb3_update(wb3_misPredIdx).taken)
    }
    // update ghr spec at if2 stage
    .otherwise {
        ghist_spec := (ghist_spec << if2_branchCount) | if2_packedTaken(fetchWidth - 1)
    }
    // for(i <- 0 until p(FETCH_WIDTH)) {
    //     when(io.update(i).valid) {
    //         printf(p"UPDATE pc=${io.update(i).pc} taken=${io.update(i).taken} ctr=${io.update(i).counter},GHR=${io.update(i).ghist}\n")
    //     }
    //     when(io.if1_predict(i).valid) {
    //         printf(p"PRED pc=${RegNext(io.if0_req(i).pc)} ctr=${io.if1_predict(i).counter}\n")
    //     }
    //     // printf(p"REQ pc=${io.if0_req(i).pc}, valid:${io.if0_req(i).valid}\n")
    // }
}