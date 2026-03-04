package MyCPU.frontend.BPU.predictors

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._

import org.chipsalliance.cde.config._
import MyCPU.config._
import MyCPU.io._

class NullPredictor(implicit p: Parameters) extends Predictor {
    for(i <- 0 until p(FETCH_WIDTH)) {
        io.if0_predict(i).valid := io.if0_req(i).valid
        io.if0_predict(i).counter := 0.U
        io.if0_predict(i).ghist := 0.U
        io.if0_predict(i).lhist := 0.U

        io.if1_predict(i) := 0.U.asTypeOf(new PredictorRespInfo)
    }
}