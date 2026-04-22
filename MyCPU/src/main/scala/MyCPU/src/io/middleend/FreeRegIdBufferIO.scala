package MyCPU.io

import chisel3._
import chisel3.util._

import MyCPU.config._
import org.chipsalliance.cde.config._

class FreeRegIdBufferReadReq(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val is_checkPoint = Bool()
}

class FreeRegIdBufferReadResp(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val preg = UInt(p(PREG_WIDTH).W)
    val is_checkPoint = Bool()
}

class FreeRegIdBufferWriteReq(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val preg = UInt(p(PREG_WIDTH).W)
}