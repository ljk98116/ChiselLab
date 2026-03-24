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
        val if1_flush = Input(Bool())
        val if2_flush = Input(Bool())
        val commit_flush = Input(Bool())
        // stall req
        val if0_stall = Input(Bool())
        val if1_stall = Input(Bool())
        val if2_stall = Input(Bool())
        // from if0
        val if0_req = Input(Vec(p(FETCH_WIDTH), new PredictorReqInfo))
        // from if1
        val if1_l0_btb_hit = Input(Vec(p(FETCH_WIDTH), Bool()))
        // redirect
        val if3_redirect = Input(new IF3RedirectInfo)
        // with different banks
        val update = Input(Vec(p(FETCH_WIDTH), new PredictorUpdateInfo))
        // predict result
        val if0_predict = Output(Vec(p(FETCH_WIDTH), new PredictorRespInfo))
        val if1_predict = Output(Vec(p(FETCH_WIDTH), new PredictorRespInfo))
        // predecode result from if2
        val if2_predecode = Input(Vec(p(FETCH_WIDTH), new ICacheBranchMetaInfo))
    })
}