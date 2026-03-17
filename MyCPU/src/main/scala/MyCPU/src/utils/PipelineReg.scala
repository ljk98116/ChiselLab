package MyCPU.utils

import chisel3._
import chisel3.util._

import MyCPU.config._
import MyCPU.io._
import MyCPU.frontend.BPU._
import MyCPU.frontend.BPU.predictors._
import org.chipsalliance.cde.config._

object PipeReg {
    def apply[T <: Data](gen: T, stall: Bool = false.B, flush: Bool = false.B): T = {
        val pipe  = RegInit(0.U.asTypeOf(gen))
        when(flush) {
            pipe := 0.U.asTypeOf(gen)
        }.elsewhen(!stall) {
            pipe := gen
        }
        pipe
    }
}