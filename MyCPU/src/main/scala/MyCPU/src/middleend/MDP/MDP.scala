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
        val flush = Input(Bool())
        val stall_from_backpipe = Input(Bool())
        val rename2_stall = Input(Bool())
        val rename1_stall = Input(Bool())
        val loadRAWHarvardInfo = Flipped(DecoupledIO(new LoadRAWHarvardInfo))
        val decode_pcs = Input(Vec(p(FETCH_WIDTH), UInt(p(ADDR_WIDTH).W)))
        val decode_pcValids = Input(Vec(p(FETCH_WIDTH), Bool()))
        val rename1_pcs = Input(Vec(p(FETCH_WIDTH), UInt(p(ADDR_WIDTH).W)))
        val rename1_robIds = Input(Vec(p(FETCH_WIDTH), UInt(p(ROBID_WIDTH).W)))
        val rename1_isStoreVec = Input(Vec(p(FETCH_WIDTH), Bool()))
        val rename1_isLoadVec = Input(Vec(p(FETCH_WIDTH), Bool()))
        val dispatch1_canSpecExec = Output(Vec(p(FETCH_WIDTH), Bool())) // only for load insts, output at dispatch1 stage
    })
}