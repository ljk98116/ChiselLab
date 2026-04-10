package MyCPU.io

import chisel3._
import chisel3.util._

import MyCPU.config._
import org.chipsalliance.cde.config._

class DecodeResultBundle(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val pc = UInt(p(ADDR_WIDTH).W)
    val imm = UInt(p(DATA_WIDTH).W)
    val rs1 = UInt(p(AREG_WIDTH).W)
    val rs2 = UInt(p(AREG_WIDTH).W)
    val rd = UInt(p(AREG_WIDTH).W)
    val funct3 = UInt(3.W)
    val funct7 = UInt(7.W)
    val opcode = UInt(7.W)
    val lat = UInt(3.W)
    val HasRs1 = Bool()
    val HasRs2 = Bool()
    val HasRd = Bool()
}