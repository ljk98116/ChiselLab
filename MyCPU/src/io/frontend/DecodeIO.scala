package MyCPU.io

import chisel3._
import chisel3.util._

import MyCPU.config._

class DecodeResultBundle extends Bundle {
    val imm = UInt(CPUConfig.DATA_WIDTH.W)
    val rs1 = UInt(CPUConfig.AREG_WIDTH.W)
    val rs2 = UInt(CPUConfig.AREG_WIDTH.W)
    val rd = UInt(CPUConfig.AREG_WIDTH.W)
    val funct3 = UInt(3.W)
    val funct7 = UInt(7.W)
    val opcode = UInt(7.W)
}