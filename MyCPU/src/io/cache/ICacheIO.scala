package MyCPU.io

import chisel3._
import chisel3.util._

import MyCPU.config._

class ICacheLineEntry extends Bundle {
    val valid = Bool();
    val tag = UInt(CacheConfig.icache_tag_width.W);
    val index = UInt(CacheConfig.icache_line_idx_width.W);
    val wayIndex = UInt(CacheConfig.icache_way_idx_width.W);
    val data = Vec(CacheConfig.icache_line_data_num, UInt(CPUConfig.DATA_WIDTH.W)); 
}

class ICacheReadReq extends Bundle {
    val en = Bool();
    val addr = UInt(CPUConfig.ADDR_WIDTH.W);
}