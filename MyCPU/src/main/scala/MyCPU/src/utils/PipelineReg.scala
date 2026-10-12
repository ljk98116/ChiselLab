package MyCPU.utils

import chisel3._
import chisel3.util._

import MyCPU.config._
import MyCPU.io._
import MyCPU.frontend.BPU._
import MyCPU.frontend.BPU.predictors._
import org.chipsalliance.cde.config._

object PipeReg {
    def apply[T <: Data](
        data:  T,
        stall: Bool = false.B,
        flush: Bool = false.B
    ): T = {
        val reg = RegInit(0.U(data.getWidth.W))
        when(flush) {
            reg := 0.U
        }.elsewhen(!stall) {
            reg := data.asUInt
        }

        // 关键：用 Wire 承接 asTypeOf 的结果，返回可写的 Wire
        val out = Wire(data.cloneType)
        out := reg.asTypeOf(data.cloneType)
        out
    }
}