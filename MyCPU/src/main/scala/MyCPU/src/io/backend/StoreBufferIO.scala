package MyCPU.io

import chisel3._
import chisel3.util._

import MyCPU.config._
import org.chipsalliance.cde.config._

class StoreBufferRespInfo(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val data = UInt(p(DATA_WIDTH).W)
}