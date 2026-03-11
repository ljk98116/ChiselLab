package MyCPU.cache

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._

// need to designed as multiport
class DCache(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val read_reqs = Input(Vec(p(FETCH_WIDTH), new DCacheReadReq));
        val write_reqs = Input(Vec(p(FETCH_WIDTH), new DCacheWriteReq));
    });
}