package MyCPU.io

import chisel3._
import chisel3.util._

import MyCPU.config._
import org.chipsalliance.cde.config._

class BPUPredictInfo(implicit p: Parameters) extends Bundle {
    val pc = UInt(p(ADDR_WIDTH).W)
    val taken = Bool()
    val target = UInt(p(ADDR_WIDTH).W)
}

class BPUUpdateInfo(implicit p: Parameters) extends Bundle {
    val pc = UInt(p(ADDR_WIDTH).W)
    val taken = Bool()
    val target = UInt(p(ADDR_WIDTH).W)
    val misPred = Bool()
}

