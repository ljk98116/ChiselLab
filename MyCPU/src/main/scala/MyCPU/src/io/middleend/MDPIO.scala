package MyCPU.io

import chisel3._
import chisel3.util._

import MyCPU.config._
import org.chipsalliance.cde.config._

class SSITEntry(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val LFSTIDs = Vec(p(SLOT_NUM), UInt(log2Ceil(p(SSIT_SIZE)).W))
    val LFSTValids = Vec(p(SLOT_NUM), Bool())
}

class LFSTEntry(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val pc = UInt(p(ADDR_WIDTH).W)
}