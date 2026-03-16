package MyCPU

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

class PRF(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val read_reqs = Input(Vec(p(FETCH_WIDTH) * 2, new PRFReadReq))
        val write_reqs = Input(Vec(p(FETCH_WIDTH), new PRFWriteReq))
        val read_res = Output(Vec(p(FETCH_WIDTH) * 2, new PRFReadRes))
    })

}