package MyCPU.io

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._

class DCacheLineEntry(implicit p: Parameters) extends Bundle {
    val valid = Bool();
    val tag = UInt(p(dcache_tag_width).W);
    val index = UInt(p(dcache_line_idx_width).W);
    val wayIndex = UInt(p(dcache_way_idx_width).W);
    val data = Vec(p(dcache_line_data_num), UInt(p(DATA_WIDTH).W)); 
}

class DCacheReadReq(implicit p: Parameters) extends Bundle {
    val en = Bool();
    val addr = UInt(p(ADDR_WIDTH).W);
}

class DCacheWriteReq(implicit p: Parameters) extends Bundle {
    val en = Bool();
    val addr = UInt(p(ADDR_WIDTH).W);
    val mask = UInt(p(DATA_WIDTH).W);
    val data = UInt(p(DATA_WIDTH).W);
}