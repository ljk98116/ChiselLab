package MyCPU.frontend.BPU.predictors

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._

import org.chipsalliance.cde.config._
import MyCPU.config._
import MyCPU.io._

// gshare predictor is accessed in if1 stage, update when commit
// if0 stage generate pht index, deal with bank conflict
class GSharePredictor(implicit p: Parameters) extends Predictor {
    val fetchWidth = p(FETCH_WIDTH)
    val indexBits = p(GShareParamKey).BPUPredictorIndexBits
    val globalHistoryBits = p(GShareParamKey).BPUPredictorGlobalHistoryBits
    val counterBits = p(GShareParamKey).BPUPredictorCounterBits

    val predictorSize = 1 << indexBits
    val phtBanks = Seq.fill(fetchWidth)(
        SyncReadMem(predictorSize / fetchWidth, UInt(counterBits.W))
    )

    var ghist = RegInit(0.U(globalHistoryBits.W))

    val num_branch = PopCount(io.update.map(_.valid))

    // predictor table
    // phtIndex: (pc >> 2) ^ ghr, get pht item at if1 stage
    // read predictor table at if0 stage
    val s0_phtReqIndex = io.req.map(
        req => (req.pc >> 2)(indexBits - 1, 0) ^ ghist(globalHistoryBits - 1, globalHistoryBits - indexBits)
    )
    val s0_phtReqIndexBankIds = s0_phtReqIndex.map(
        phtReqIndex => phtReqIndex(log2Ceil(fetchWidth) - 1, 0) 
    )
    val s0_phtReqIndexBankRow = s0_phtReqIndex.map(
        phtReqIndex => phtReqIndex(indexBits - 1, log2Ceil(fetchWidth))
    )
    val s0_phtAccess = io.req.map(_.valid)

    // need to handle bank conflict
    // decide current phtbank req (from if0/skid buffer)
    

    // output is the counter value, which will be used to generate prediction result at if2 stage
    val phtReadData = WireInit(VecInit(
        Seq.fill(fetchWidth)(0.U(counterBits.W))
    ))


}