package MyCPU.io

import chisel3._
import chisel3.util._

import MyCPU.config._
import org.chipsalliance.cde.config._

class ROBSramEntry(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val robId = UInt(p(ROBID_WIDTH).W)
    val pc = UInt(p(ADDR_WIDTH).W)
    val opcode = UInt(7.W)
    val rd = UInt(p(AREG_WIDTH).W)
    val oldpd = UInt(p(PREG_WIDTH).W)
    val result = UInt(p(DATA_WIDTH).W)
    val exceptionType = UInt(4.W)
    val branchPredInfo = new BranchPredInfo
}