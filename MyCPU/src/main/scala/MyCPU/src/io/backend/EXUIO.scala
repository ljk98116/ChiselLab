package MyCPU.io

import chisel3._
import chisel3.util._

import MyCPU.config._
import org.chipsalliance.cde.config._

class PRFWriteReq(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val HasRd = Bool()
    val result = UInt(p(DATA_WIDTH).W)
    val rd = UInt(p(AREG_WIDTH).W)
    val pd = UInt(p(PREG_WIDTH).W)
}

class ALUResult(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val HasRd = Bool()
    val result = UInt(p(DATA_WIDTH).W)
    val pd = UInt(p(PREG_WIDTH).W)
    val robLoc = UInt(p(ROBID_WIDTH).W)
    val branchPredInfo = new BranchPredInfo
}

class AGUResult(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val HasRd = Bool()
    val addr = UInt(p(DATA_WIDTH).W)
    val wdata = UInt(p(DATA_WIDTH).W)
    val mask = UInt(p(DATA_WIDTH).W)
    val is_sign_ext = Bool()
    val pd = UInt(p(PREG_WIDTH).W)
    val robLoc = UInt(p(ROBID_WIDTH).W)
    val wen = Bool()
    val pc = UInt(p(ADDR_WIDTH).W)
}

class LDUResult(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val HasRd = Bool()
    val result = UInt(p(DATA_WIDTH).W)
    val pd = UInt(p(PREG_WIDTH).W)
    val robLoc = UInt(p(ROBID_WIDTH).W)
}

class SysResult(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val HasRd = Bool()
    val result = UInt(p(DATA_WIDTH).W)
    val pd = UInt(p(PREG_WIDTH).W)
    val robLoc = UInt(p(ROBID_WIDTH).W)
}

class ALUInputInfo(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val HasRd = Bool()
    val pd = UInt(p(PREG_WIDTH).W)
    val robLoc = UInt(p(ROBID_WIDTH).W)
    val branchPredInfo = new BranchPredInfo
}

class LDUInputInfo(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val pc = UInt(p(ADDR_WIDTH).W)
    val imm = UInt(p(DATA_WIDTH).W)
    val funct3 = UInt(3.W)
    val opcode = UInt(7.W)
    val HasRd = Bool()
    val pd = UInt(p(PREG_WIDTH).W)
    val robLoc = UInt(p(ROBID_WIDTH).W)    
    val ps1_data = UInt(p(DATA_WIDTH).W)  
}

class STUInputInfo(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val pc = UInt(p(ADDR_WIDTH).W)
    val imm = UInt(p(DATA_WIDTH).W)
    val funct3 = UInt(3.W)
    val opcode = UInt(7.W)
    val HasRd = Bool()
    val pd = UInt(p(PREG_WIDTH).W)
    val robLoc = UInt(p(ROBID_WIDTH).W)
    val ps1_data = UInt(p(DATA_WIDTH).W)  
    val ps2_data = UInt(p(DATA_WIDTH).W)
}

class SYSEXUInputInfo(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val pc = UInt(p(ADDR_WIDTH).W)
    val imm = UInt(p(DATA_WIDTH).W)
    val funct3 = UInt(3.W)
    val funct7 = UInt(7.W)
    val opcode = UInt(7.W)
    val HasRd = Bool()
    val pd = UInt(p(PREG_WIDTH).W)
    val robLoc = UInt(p(ROBID_WIDTH).W)
    val branchPredInfo = new BranchPredInfo
    val ps1_data = UInt(p(DATA_WIDTH).W)  
    val ps2_data = UInt(p(DATA_WIDTH).W)
}

class IntegerUnitInputInfo(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val funct3 = UInt(3.W)
    val funct7 = UInt(7.W)
    val data1 = UInt(p(DATA_WIDTH).W)
    val data2 = UInt(p(DATA_WIDTH).W)
    val pc = UInt(p(ADDR_WIDTH).W)
    val imm = UInt(p(DATA_WIDTH).W)
    val is_lui = Bool()
    val is_auipc = Bool()
    val is_i_type = Bool()
}

class BranchUnitInputInfo(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val is_jal = Bool()
    val funct3 = UInt(3.W)
    val data1 = UInt(p(DATA_WIDTH).W)
    val data2 = UInt(p(DATA_WIDTH).W) 
    val pc = UInt(p(ADDR_WIDTH).W)
    val imm = UInt(p(DATA_WIDTH).W)   
}

class MultUnitInputInfo(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Input(Bool())
    val funct3 = Input(UInt(3.W)) 
    val data1 = Input(UInt(p(DATA_WIDTH).W))
    val data2 = Input(UInt(p(DATA_WIDTH).W))
}

class DivUnitInputInfo(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Input(Bool())
    val funct3 = Input(UInt(3.W)) 

    val data1 = Input(UInt(p(DATA_WIDTH).W))
    val data2 = Input(UInt(p(DATA_WIDTH).W))    
}