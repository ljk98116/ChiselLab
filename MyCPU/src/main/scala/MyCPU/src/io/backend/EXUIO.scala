package MyCPU.io

import chisel3._
import chisel3.util._

import MyCPU.config._
import org.chipsalliance.cde.config._

class ALUResult(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val HasRd = Bool()
    val result = UInt(p(DATA_WIDTH).W)
    val rd = UInt(p(AREG_WIDTH).W)
    val pd = UInt(p(PREG_WIDTH).W)
}

class MemResult(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val HasRd = Bool()
    val result = UInt(p(DATA_WIDTH).W)
    val rd = UInt(p(AREG_WIDTH).W)
    val pd = UInt(p(PREG_WIDTH).W)
}

class SysResult(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val HasRd = Bool()
    val result = UInt(p(DATA_WIDTH).W)
    val rd = UInt(p(AREG_WIDTH).W)
    val pd = UInt(p(PREG_WIDTH).W)
}