package MyCPU.frontend.BPU.predictors

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._

import org.chipsalliance.cde.config._
import MyCPU.config._
import MyCPU.io._

class NullPredictor(implicit p: Parameters) extends Predictor {
    for(i <- 0 until p(FETCH_WIDTH)) {
        io.predict(i).valid := io.req(i).valid
        io.predict(i).taken := false.B
    }
}