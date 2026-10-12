package MyCPU.utils

import chisel3._
import chisel3.util._

import MyCPU.config._
import MyCPU.io._
import MyCPU.frontend._
import MyCPU.middleend._
import MyCPU.backend._
import MyCPU.cache._
import org.chipsalliance.cde.config._
import MyCPU.utils._

// local processor state control
// gen redirect msg from last processor state
// priority: last redirect > local redirect
// recv then clear
class RedirectBroadcaster(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    val last_stage_redirect = Input(new CommitUpdateInfo)
    val redirect_output = Output(new CommitUpdateInfo)
    val last_stage_ready = Input(Bool())
    val ready_output = Output(Bool())
  })
  // hold as Q
  val redirectReg = RegInit(0.U.asTypeOf(new CommitUpdateInfo))
  // hold as D flipflop
  val readyReg = RegInit(false.B)

  when(io.last_stage_redirect.flush) {
    redirectReg := io.last_stage_redirect
  }.otherwise {
    redirectReg := 0.U.asTypeOf(new CommitUpdateInfo)
  }

  readyReg := io.last_stage_ready

  io.redirect_output := redirectReg
  io.ready_output := readyReg
}