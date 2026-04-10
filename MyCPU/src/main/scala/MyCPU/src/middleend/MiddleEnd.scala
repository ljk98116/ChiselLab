package MyCPU.middleend

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._

import org.chipsalliance.cde.config._
import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

// 5 stage
// decode -> rename1 -> rename2 -> dispatch1 -> dispatch2
class MiddleEnd(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        
    })
}