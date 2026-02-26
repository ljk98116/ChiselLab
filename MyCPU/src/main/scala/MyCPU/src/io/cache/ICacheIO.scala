package MyCPU.io

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._

class ICacheLineEntry(implicit p: Parameters) extends Bundle {
    val valid = Bool();
    val tag = UInt(p(icache_tag_width).W);
    val index = UInt(p(icache_line_idx_width).W);
    val wayIndex = UInt(p(icache_way_idx_width).W);
    val data = Vec(p(icache_line_data_num), UInt(p(DATA_WIDTH).W)); 
}

class ICacheReadReq(implicit p: Parameters) extends Bundle {
    val en = Bool();
    val addr = UInt(p(ADDR_WIDTH).W);
}

class ICacheBranchMetaInfo(implicit p: Parameters) extends Bundle {
    val isCall = Bool()
    val isReturn = Bool()
    val isDirectJump = Bool()
    val isIndirectJump = Bool()
    val isConditionalBranch = Bool()
    val isUnconditionalBranch = Bool()
}