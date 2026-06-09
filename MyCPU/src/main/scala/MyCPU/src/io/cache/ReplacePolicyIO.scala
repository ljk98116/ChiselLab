package MyCPU.io

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._

class ReplacePolicyHitUpdateInfo(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val index = UInt(p(ICacheIndexBits).W)
    val way = UInt(log2Ceil(p(L1CacheWayNum)).W)
}

class ReplacePolicyRefillUpdateInfo(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val index = UInt(p(ICacheIndexBits).W)
    val way = UInt(log2Ceil(p(L1CacheWayNum)).W)
}

class ICacheReplacePolicyRefillUpdateInfo(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val index = UInt(p(ICacheIndexBits).W)
    val way = UInt(log2Ceil(p(L1CacheWayNum)).W)
}

class DCacheReplacePolicyRefillUpdateInfo(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val index = UInt(p(DCacheIndexBits).W)
    val way = UInt(log2Ceil(p(L1CacheWayNum)).W)
}

class ReplacePolicyRefillReqInfo(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val index = UInt(p(ICacheIndexBits).W)
}

class ReplacePolicyRefillRespInfo(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val way = UInt(log2Ceil(p(L1CacheWayNum)).W)
}