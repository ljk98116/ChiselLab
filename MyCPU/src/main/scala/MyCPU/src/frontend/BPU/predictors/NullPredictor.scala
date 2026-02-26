package MyCPU.frontend.BPU.predictors

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._

import org.chipsalliance.cde.config._
import MyCPU.config._
import MyCPU.io._

class NullPredictor(implicit p: Parameters) extends Predictor {
    io.predict.valid := io.req.valid
    io.predict.taken := false.B
}