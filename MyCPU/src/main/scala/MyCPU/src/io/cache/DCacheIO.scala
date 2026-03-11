package MyCPU.io

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._

class DCacheLineEntry(implicit p: Parameters) extends Bundle {
    val valid = Bool()
    val tag = UInt(p(DCacheTagBits).W)
    val data = Vec(p(L1CacheLineLength), UInt(p(DATA_WIDTH).W))
}

class DCacheReadReq(implicit p: Parameters) extends Bundle {
    val en = Bool()
    val addr = UInt(p(ADDR_WIDTH).W)
}

class DCacheWriteReq(implicit p: Parameters) extends Bundle {
    val en = Bool()
    val addr = UInt(p(ADDR_WIDTH).W)
    val mask = UInt(p(DATA_WIDTH).W)
    val data = UInt(p(DATA_WIDTH).W)
}