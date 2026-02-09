package MyCPU.cache

import chisel3._
import chisel3.util._

import MyCPU.config._
import MyCPU.io._

class DCache extends Module {
    val io = IO(new Bundle {
        val read_reqs = Input(Vec(CPUConfig.FETCH_WIDTH, new DCacheReadReq));
        val write_reqs = Input(Vec(CPUConfig.FETCH_WIDTH, new DCacheWriteReq));
    });
}