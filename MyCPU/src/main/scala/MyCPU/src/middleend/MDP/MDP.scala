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
        val isLoadVec = Input(Vec(p(FETCH_WIDTH), Bool()))
        val isStoreVec = Input(Vec(p(FETCH_WIDTH), Bool()))
        val pcVec = Input(Vec(p(FETCH_WIDTH), UInt(p(ADDR_WIDTH).W)))
        val canSpecExec = Output(Vec(p(FETCH_WIDTH), Bool())) // only for load insts
    })
}