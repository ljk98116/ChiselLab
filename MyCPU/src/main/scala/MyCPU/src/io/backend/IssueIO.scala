package MyCPU.io

import chisel3._
import chisel3.util._

import MyCPU.config._
import org.chipsalliance.cde.config._

class ALUIQEntry(implicit p: Parameters) extends Bundle with HasValid {
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
    val branchPredInfo = new BranchPredInfo
    val rdy1 = Bool()
    val rdy2 = Bool()
    val robLoc = UInt(p(ROBID_WIDTH).W)
}

class SYSIQEntry(implicit p: Parameters) extends ALUIQEntry

class LDUIQEntry(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val pc = UInt(p(ADDR_WIDTH).W)
    val imm = UInt(p(DATA_WIDTH).W)
    val rs1 = UInt(p(AREG_WIDTH).W)
    val rs2 = UInt(p(AREG_WIDTH).W)
    val rd = UInt(p(AREG_WIDTH).W)
    val funct3 = UInt(3.W)
    val funct7 = UInt(7.W)
    val opcode = UInt(7.W)
    val HasRs1 = Bool()
    val HasRs2 = Bool()
    val HasRd = Bool()
    val ps1 = UInt(p(PREG_WIDTH).W)
    val ps2 = UInt(p(PREG_WIDTH).W)
    val pd = UInt(p(PREG_WIDTH).W)
    val old_pd = UInt(p(PREG_WIDTH).W)
    val rob_id = UInt(p(ROBID_WIDTH).W)
    val LDSpecExec = Bool()
    val branchPredInfo = new BranchPredInfo
    val rdy1 = Bool()
    val rdy2 = Bool()
    val robLoc = UInt(p(ROBID_WIDTH).W)    
    val canSpecExec = Vec(p(SLOT_NUM), Bool())
    val blockStIds = Vec(p(SLOT_NUM), UInt(p(ROBID_WIDTH).W))
}

class ALUIQFreeIdBufferWriteReq(implicit p: Parameters) extends Bundle with HasValid {
    val totalSize = p(ALUIQ_SIZE)
    val ptrWidth = log2Ceil(totalSize)

    val valid = Bool()
    val id = UInt(ptrWidth.W)
}

class LDUIQFreeIdBufferWriteReq(implicit p: Parameters) extends Bundle with HasValid {
    val totalSize = p(LDUIQ_SIZE)
    val ptrWidth = log2Ceil(totalSize)

    val valid = Bool()
    val id = UInt(ptrWidth.W)    
}

class StoreQueueEnable(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val robLoc = UInt(p(ROBID_WIDTH).W)
}

class LoadQueueEnable(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val robLoc = UInt(p(ROBID_WIDTH).W)
}

class STUIQEntry(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val pc = UInt(p(ADDR_WIDTH).W)
    val imm = UInt(p(DATA_WIDTH).W)
    val rs1 = UInt(p(AREG_WIDTH).W)
    val rs2 = UInt(p(AREG_WIDTH).W)
    val rd = UInt(p(AREG_WIDTH).W)
    val funct3 = UInt(3.W)
    val funct7 = UInt(7.W)
    val opcode = UInt(7.W)
    val HasRs1 = Bool()
    val HasRs2 = Bool()
    val HasRd = Bool()
    val ps1 = UInt(p(PREG_WIDTH).W)
    val ps2 = UInt(p(PREG_WIDTH).W)
    val pd = UInt(p(PREG_WIDTH).W)
    val old_pd = UInt(p(PREG_WIDTH).W)
    val rob_id = UInt(p(ROBID_WIDTH).W)
    val LDSpecExec = Bool()
    val branchPredInfo = new BranchPredInfo
    val rdy1 = Bool()
    val rdy2 = Bool()    
    val atRobHead = Bool()
    val robLoc = UInt(p(ROBID_WIDTH).W)
}
