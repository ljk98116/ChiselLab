package MyCPU

import chisel3._
import chisel3.util._

import MyCPU.config._
import MyCPU.io._
import MyCPU.frontend._
import MyCPU.middleend._
import MyCPU.backend._
import org.chipsalliance.cde.config._
import MyCPU.utils._

class MyCPU(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    
  })
}