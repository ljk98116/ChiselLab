package MyCPU.io

import chisel3._
import chisel3.util._

import MyCPU.config._
import org.chipsalliance.cde.config._

class ROBFreeIdAllocReq(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
}

class ROBFreeIdAllocResp(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val robId = UInt(p(ROBID_WIDTH).W)
}

class ROBFreeIdWriteReq(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val robId = UInt(p(ROBID_WIDTH).W)
}