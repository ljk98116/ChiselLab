package MyCPU.middleend

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._

import org.chipsalliance.cde.config._
import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

class RenameStages(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val stall_from_backpipe = Input(Bool())
        val flush = Input(Bool())
        // from if5
        val decodeRes = Input(Vec(p(FETCH_WIDTH), new DecodeResultBundle))
        val br_pred_info_vec = Input(Vec(p(FETCH_WIDTH), new BranchPredInfo))
        // to RATSpec
        val rs1_rat_read_req = Output(Vec(p(FETCH_WIDTH), new RatSpecReadReq))
        val rs2_rat_read_req = Output(Vec(p(FETCH_WIDTH), new RatSpecReadReq))
        val rd_rat_read_req = Output(Vec(p(FETCH_WIDTH), new RatSpecReadReq))
        val rd_rat_write_req = Output(Vec(p(FETCH_WIDTH), new RatSpecWriteReq))
        // from RATSpec
        val rs1_rat_read_resp = Input(Vec(p(FETCH_WIDTH), new RATSpecReadResp))
        val rs2_rat_read_resp = Input(Vec(p(FETCH_WIDTH), new RATSpecReadResp))
        val rd_rat_read_resp = Input(Vec(p(FETCH_WIDTH), new RATSpecReadResp))
        // freeRegIdBuffer io
        val freeRegBufferReadReq = Output(Vec(p(FETCH_WIDTH), new FreeRegBufferReadReq))
        val freeRegBufferReadResp = Output(Vec(p(FETCH_WIDTH), new FreeRegBufferReadResp))
        // 
    })

}