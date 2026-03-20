package MyCPU.frontend.BPU.predictors

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._

import org.chipsalliance.cde.config._
import MyCPU.config._
import MyCPU.io._

// abstract class Predictor, which is the parent class of all predictors
abstract class Predictor(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val flush = Input(Bool())
        // stall req
        val if1_stall = Input(Bool())
        val if2_stall = Input(Bool())
        // from if0
        val if0_req = Input(Vec(p(FETCH_WIDTH), new PredictorReqInfo))
        // with different banks
        val update = Input(Vec(p(FETCH_WIDTH), new PredictorUpdateInfo))
        // predict result
        val if0_predict = Output(Vec(p(FETCH_WIDTH), new PredictorRespInfo))
        val if1_predict = Output(Vec(p(FETCH_WIDTH), new PredictorRespInfo))
        // predecode result from if1
        // because we use pageoffset to access icache
        val if1_predecode = Input(Vec(p(FETCH_WIDTH), new ICacheBranchMetaInfo))
        val if0_stall = Output(Bool())
    })
}