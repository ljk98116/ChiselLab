package MyCPU.io

import chisel3._
import chisel3.util._

import MyCPU.config._
import org.chipsalliance.cde.config._

class L0BTBReqInfo(implicit p: Parameters) extends Bundle with HasValid {
    val pc = UInt(p(ADDR_WIDTH).W)
    val valid = Bool()
}

class L0BTBRespInfo(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val target = UInt(p(ADDR_WIDTH).W)
    val isCall = Bool()
    val isReturn = Bool()
    val hitIndex = UInt(log2Ceil(p(FETCH_WIDTH)).W)
}

class L0BTBUpdateInfo(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val pc = UInt(p(ADDR_WIDTH).W)
    val target = UInt(p(ADDR_WIDTH).W)
    val isCall = Bool()
    val isReturn = Bool()
}

class L1BTBReqInfo(implicit p: Parameters) extends Bundle with HasValid {
    val pc = UInt(p(ADDR_WIDTH).W)
    val valid = Bool()
    val l0_hit = Bool()
}

class L1BTBRespInfo(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val target = UInt(p(ADDR_WIDTH).W)
    val isCall = Bool()
    val isReturn = Bool()
}

class L1BTBUpdateInfo(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val pc = UInt(p(ADDR_WIDTH).W)
    val target = UInt(p(ADDR_WIDTH).W)
    val isCall = Bool()
    val isReturn = Bool()
}
