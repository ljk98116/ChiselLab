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
        val req = Input(Vec(p(FETCH_WIDTH), new PredictorReqInfo))
        // with different banks
        val update = Input(Vec(p(FETCH_WIDTH), new PredictorUpdateInfo))
        val predict = Output(Vec(p(FETCH_WIDTH), new PredictorRespInfo))
    })
}