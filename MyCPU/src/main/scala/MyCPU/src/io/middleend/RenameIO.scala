package MyCPU.io

import chisel3._
import chisel3.util._

import MyCPU.config._
import org.chipsalliance.cde.config._

class uOp(implicit p: Parameters) extends Bundle with HasValid {
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
    val ps1 = UInt(p(PREG_WIDTH).W)
    val ps2 = UInt(p(PREG_WIDTH).W)
    val pd = UInt(p(PREG_WIDTH).W)
    val old_pd = UInt(p(PREG_WIDTH).W)
    val rob_id = UInt(p(ROBID_WIDTH).W)
    val rob_loc = UInt(p(ROBID_WIDTH).W)
    val LDSpecExec = Vec(p(SLOT_NUM), Bool())
    val blockStIds = Vec(p(SLOT_NUM), UInt(p(ROBID_WIDTH).W))
    val branchPredInfo = new BranchPredInfo
}

class CheckPointInfo(implicit p: Parameters) extends Bundle with HasValid { 
    val totalSize =  1 << p(PREG_WIDTH)
    val perBankSize = totalSize / p(FETCH_WIDTH)
    val ptrWidth = log2Ceil(perBankSize)
    
    val valid = Bool()
    val freeRegIdBufferHeads = Vec(p(FETCH_WIDTH), UInt(ptrWidth.W))
    val busyTable = UInt(totalSize.W)
}