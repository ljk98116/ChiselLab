package MyCPU.frontend

import chisel3._
import chisel3.util._

import MyCPU.config._
import MyCPU.io._
import MyCPU.frontend.BPU._
import MyCPU.frontend.BPU.predictors._
import org.chipsalliance.cde.config._

// if1 stage: access L1 BTB, generate branch prediction result for later stage
// if1 stage also receives l0_btb_resp from if0 stage, used for if1 redirect and if1 branch prediction
// if1 stage compare PA & icache tag
class IF1Stage(implicit p: Parameters) extends Module {

}