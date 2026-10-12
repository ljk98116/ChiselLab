package MyCPU.io

import chisel3._
import chisel3.util._

import MyCPU.config._
import org.chipsalliance.cde.config._

class RATCommitReadReq(implicit p: Parameters) extends Bundle with HasValid {
    val areg = UInt(p(AREG_WIDTH).W)
    val valid = Bool()
}

class RATCommitReadResp(implicit p: Parameters) extends Bundle with HasValid {
    val preg = UInt(p(PREG_WIDTH).W)
    val valid = Bool()
}

class RATCommitWriteReq(implicit p: Parameters) extends Bundle with HasValid {
  val areg = UInt(p(AREG_WIDTH).W)
  val preg = UInt(p(PREG_WIDTH).W)
  val valid = Bool()
}

class RATCommitEntry(implicit p: Parameters) extends Bundle {
  val preg = UInt(p(PREG_WIDTH).W)
  val valid = Bool()
}