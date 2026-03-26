package MyCPU.io

import chisel3._
import chisel3.util._

import MyCPU.config._
import org.chipsalliance.cde.config._

class CommitUpdateInfo(implicit p: Parameters) extends Bundle {
    val flush = Bool()
    val flush_pc = UInt(p(ADDR_WIDTH).W)
    val pc = UInt(p(ADDR_WIDTH).W)
    val isCall = Bool()
    val isReturn = Bool()
    val rasTopPtr = UInt(log2Ceil(p(RASParam).RASSize + 1).W)
}