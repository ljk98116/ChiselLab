package MyCPU.io

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._

class ICacheLineEntry(implicit p: Parameters) extends Bundle {
    val valid = Bool()
    val tag = UInt(p(ICacheTagBits).W)
    val data = Vec(p(L1CacheLineLength), UInt(p(DATA_WIDTH).W)) 
}

class ICacheReadReq(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val index = UInt(p(ICacheIndexBits).W)
    val offset = UInt(p(L1CacheOffsetBits).W)
}

class ICacheLine(implicit p: Parameters) extends Bundle {
    val datas = Vec(p(L1CacheLineLength), UInt(p(DATA_WIDTH).W))
}

class ICacheRefillResp(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val pa = new PhysicalAddress
    val data = Vec(p(L1CacheLineLength), UInt(p(DATA_WIDTH).W))
}

class ICacheBranchMetaInfo(implicit p: Parameters) extends Bundle {
    val isCall = Bool()
    val isReturn = Bool()
    val isDirectJump = Bool()
    val isIndirectJump = Bool()
    val isConditionalBranch = Bool()
    val isUnconditionalBranch = Bool()
}