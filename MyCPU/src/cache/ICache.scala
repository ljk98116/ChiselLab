package MyCPU.cache

import chisel3._
import chisel3.util._

import MyCPU.config._
import MyCPU.io._

class ICache extends Module {
    val io = IO(new Bundle {
        // IF2 stage, use PC's physical address
        val read_reqs = Input(Vec(CPUConfig.FETCH_WIDTH, new ICacheReadReq));
        
    });
}