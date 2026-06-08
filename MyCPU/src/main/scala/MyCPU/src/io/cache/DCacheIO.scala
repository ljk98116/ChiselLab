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

class DCacheReq(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val addr = UInt(p(ADDR_WIDTH).W)
    val wen = Bool()
    val mask = UInt(p(DATA_WIDTH).W)
    val data = UInt(p(DATA_WIDTH).W)
    val isSignExt = Bool()
    val robLoc = UInt(p(ROBID_WIDTH).W)
}

class DCacheReadReq(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val index = UInt(p(DCacheIndexBits).W)
}

class DCacheRefillReq(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val pa = new PhysicalAddress
}

class DCacheRefillResp(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val pa = new PhysicalAddress
    val data = Vec(p(L1CacheLineLength), UInt(p(DATA_WIDTH).W))
}

class DCacheLine(implicit p: Parameters) extends Bundle {
    val datas = Vec(p(L1CacheLineLength), UInt(p(DATA_WIDTH).W))
}

class DCacheWriteBackReq(implicit p: Parameters) extends Bundle with HasValid {
    val readPortNum = p(LDU_NUM)
    val writePortNum = p(STU_NUM)
    val bankNum = writePortNum + readPortNum
    val bankBits = log2Ceil(bankNum)

    val valid = Bool()
    val cacheLine = new DCacheLine
    val pa = new PhysicalAddress // cacheline start address
    val bankId = UInt(bankBits.W)
    val hitWay = UInt(log2Ceil(p(L1CacheWayNum)).W)
}

class DCacheHitInfo(implicit p: Parameters) extends Bundle with HasValid {
    val readPortNum = p(LDU_NUM)
    val writePortNum = p(STU_NUM)
    val bankNum = writePortNum + readPortNum
    val bankBits = log2Ceil(bankNum)
    
    val valid = Bool()
    val pa = new PhysicalAddress // cacheline start address
    val hit = Bool()
    val hitWay = UInt(log2Ceil(p(L1CacheWayNum)).W)
    val data = new DCacheLine
    val robLoc = UInt(p(ROBID_WIDTH).W)
    val mask = UInt(p(DATA_WIDTH).W)
    val wdata = UInt(p(DATA_WIDTH).W)
}