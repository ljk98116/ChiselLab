package MyCPU.io

import chisel3._
import chisel3.util._

import MyCPU.config._

class DCacheLineEntry extends Bundle {
    val valid = Bool();
    val tag = UInt(CacheConfig.dcache_tag_width.W);
    val index = UInt(CacheConfig.dcache_line_idx_width.W);
    val wayIndex = UInt(CacheConfig.dcache_way_idx_width.W);
    val data = Vec(CacheConfig.dcache_line_data_num, UInt(CPUConfig.DATA_WIDTH.W)); 
}

class DCacheReadReq extends Bundle {
    val en = Bool();
    val addr = UInt(CPUConfig.ADDR_WIDTH.W);
}

class DCacheWriteReq extends Bundle {
    val en = Bool();
    val addr = UInt(CPUConfig.ADDR_WIDTH.W);
    val mask = UInt(CPUConfig.DATA_WIDTH.W);
    val data = UInt(CPUConfig.DATA_WIDTH.W);
}