package MyCPU.io

import chisel3._
import chisel3.util._

import MyCPU.config._
import org.chipsalliance.cde.config._

class RATSpecReadReq(implicit p: Parameters) extends Bundle with HasValid {
    val areg = UInt(p(AREG_WIDTH).W)
    val valid = Bool()
}

class RATSpecReadResp(implicit p: Parameters) extends Bundle with HasValid {
    val preg = UInt(p(PREG_WIDTH).W)
    val valid = Bool()
}

class RATSpecWriteReq(implicit p: Parameters) extends Bundle with HasValid {
    val areg = UInt(p(AREG_WIDTH).W)
    val preg = UInt(p(PREG_WIDTH).W)
    val valid = Bool()
}

class RATSpecEntry(implicit p: Parameters) extends Bundle {
    val preg = UInt(p(PREG_WIDTH).W)
    val valid = Bool()
}
