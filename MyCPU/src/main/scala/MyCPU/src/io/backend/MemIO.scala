package MyCPU.io

import chisel3._
import chisel3.util._

import MyCPU.config._
import org.chipsalliance.cde.config._

class LoadWbQueueItem(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val hit = Bool()
    val pa = new PhysicalAddress
    val cacheLine = new DCacheLine
    val robLoc = UInt(p(ROBID_WIDTH).W)
    val rd = UInt(p(AREG_WIDTH).W)
    val pd = UInt(p(PREG_WIDTH).W)
    val HasRd = Bool()
    val is_sign_ext = Bool()
    val mask = UInt(p(DATA_WIDTH).W)
}

class StoreWbQueueItem(implicit p: Parameters) extends Bundle with HasValid {
    val readPortNum = p(LDU_NUM)
    val writePortNum = p(STU_NUM)
    val bankNum = writePortNum + readPortNum
    val bankBits = log2Ceil(bankNum)
        
    val valid = Bool()
    val hit = Bool()
    val pa = new PhysicalAddress
    val cacheLine = new DCacheLine
    val robLoc = UInt(p(ROBID_WIDTH).W)    
    val mask = UInt(p(DATA_WIDTH).W)
    val wdata = UInt(p(DATA_WIDTH).W)
    val bankId = UInt(bankBits.W)
    val hitWay = UInt(log2Ceil(p(L1CacheWayNum)).W)
}