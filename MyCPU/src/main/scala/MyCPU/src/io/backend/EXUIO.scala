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
    val rd = UInt(p(AREG_WIDTH).W)
    val pd = UInt(p(PREG_WIDTH).W)
    val robLoc = UInt(p(ROBID_WIDTH).W)
}

class AGUResult(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val HasRd = Bool()
    val addr = UInt(p(DATA_WIDTH).W)
    val wdata = UInt(p(DATA_WIDTH).W)
    val mask = UInt(p(DATA_WIDTH).W)
    val is_sign_ext = Bool()
    val rd = UInt(p(AREG_WIDTH).W)
    val pd = UInt(p(PREG_WIDTH).W)
    val robLoc = UInt(p(ROBID_WIDTH).W)
    val wen = Bool()
    val pc = UInt(p(ADDR_WIDTH).W)
}

class LDUResult(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val HasRd = Bool()
    val result = UInt(p(DATA_WIDTH).W)
    val rd = UInt(p(AREG_WIDTH).W)
    val pd = UInt(p(PREG_WIDTH).W)
    val robLoc = UInt(p(ROBID_WIDTH).W)
}

class SysResult(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val HasRd = Bool()
    val result = UInt(p(DATA_WIDTH).W)
    val rd = UInt(p(AREG_WIDTH).W)
    val pd = UInt(p(PREG_WIDTH).W)
    val robLoc = UInt(p(ROBID_WIDTH).W)
}

class ALUInputInfo(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val uop = new ALUIQEntry
    val ps1_data = UInt(p(DATA_WIDTH).W)
    val ps2_data = UInt(p(DATA_WIDTH).W)
}

class LDUInputInfo(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val uop = new LDUIQEntry
    val ps1_data = UInt(p(DATA_WIDTH).W)  
}

class STUInputInfo(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val uop = new STUIQEntry
    val ps1_data = UInt(p(DATA_WIDTH).W)  
    val ps2_data = UInt(p(DATA_WIDTH).W)
}

class SYSEXUInputInfo(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val uop = new SYSIQEntry
    val ps1_data = UInt(p(DATA_WIDTH).W)  
    val ps2_data = UInt(p(DATA_WIDTH).W)
}

class IntegerUnitInputInfo(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val funct3 = UInt(3.W)
    val funct7 = UInt(7.W)
    val data1 = UInt(p(DATA_WIDTH).W)
    val data2 = UInt(p(DATA_WIDTH).W)
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