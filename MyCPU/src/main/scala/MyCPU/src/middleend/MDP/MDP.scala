package MyCPU.middleend

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._

import org.chipsalliance.cde.config._
import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

abstract class MDP(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val rename1_isLoadVec = Input(Vec(p(FETCH_WIDTH), Bool()))
        val rename1_isStoreVec = Input(Vec(p(FETCH_WIDTH), Bool()))
        val rename1_pcVec = Input(Vec(p(FETCH_WIDTH), UInt(p(ADDR_WIDTH).W)))
        val dispatch1_canSpecExec = Output(Vec(p(FETCH_WIDTH), Bool())) // only for load insts, output at dispatch1 stage
    })
}