package MyCPU.config

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._

import MyCPU.config._
import MyCPU.io._

import org.chipsalliance.cde.config._

case class RVRTypeInstPattern (
    val funct7: BitPat,
    val rs2: BitPat = BitPat.dontCare(5),
    val rs1: BitPat = BitPat.dontCare(5),
    val funct3: BitPat,
    val rd: BitPat = BitPat.dontCare(5),
    val opcode: BitPat
) extends DecodePattern {
    def bitPat: BitPat = funct7 ## rs2 ## rs1 ## funct3 ## rd ## opcode;
}

case class RVITypeInstPattern(
    val imm: BitPat = BitPat.dontCare(32),
    val rs1: BitPat = BitPat.dontCare(5),
    val funct3: BitPat,
    val rd: BitPat = BitPat.dontCare(5),
    val opcode: BitPat
) extends DecodePattern {
    def bitPat: BitPat = imm(11, 0) ## rs1 ## funct3 ## rd ## opcode;
}

case class RVSTypeInstPattern(
    val imm: BitPat = BitPat.dontCare(32),
    val rs2: BitPat = BitPat.dontCare(5),
    val rs1: BitPat = BitPat.dontCare(5),
    val funct3: BitPat,
    val opcode: BitPat
) extends DecodePattern {
    def bitPat: BitPat = imm(11, 5) ## rs2 ## rs1 ## funct3 ## imm(4, 0) ## opcode;
}

case class RVSBTypeInstPattern(
    val imm: BitPat = BitPat.dontCare(32),
    val rs2: BitPat = BitPat.dontCare(5),
    val rs1: BitPat = BitPat.dontCare(5),
    val funct3: BitPat,
    val opcode: BitPat
) extends DecodePattern {
    def bitPat: BitPat = imm(12) ## imm(10, 5) ## rs2 ## rs1 ## funct3 ## imm(4, 1) ## imm(11) ## opcode;
}

case class RVUTypeInstPattern(
    val imm: BitPat = BitPat.dontCare(32),
    val rd: BitPat = BitPat.dontCare(5),
    val opcode: BitPat
) extends DecodePattern {
    def bitPat: BitPat = imm(31, 12) ## rd ## opcode;
}

case class RVUJTypeInstPattern(
    val imm: BitPat = BitPat.dontCare(32),
    val rd: BitPat = BitPat.dontCare(5),
    val opcode: BitPat
) extends DecodePattern {
    def bitPat: BitPat = imm(20) ## imm(10, 1) ## imm(11) ## imm(19, 12) ## rd ## opcode;
}

class ImmField(implicit p: Parameters) extends DecodeField[DecodePattern, UInt]
{
    def name: String = "ImmField"
    def chiselType: UInt = UInt(p(DATA_WIDTH).W)
    def genTable(op: DecodePattern): BitPat = {
        if(op.isInstanceOf[RVITypeInstPattern]) {
            return BitPat.N(20) ## op.asInstanceOf[RVITypeInstPattern].imm(11, 0)
        }
        if(op.isInstanceOf[RVSTypeInstPattern]) {
            return BitPat.N(20) ## op.asInstanceOf[RVSTypeInstPattern].imm(11, 0)
        }
        if(op.isInstanceOf[RVSBTypeInstPattern]) {
            return BitPat.N(19) ## op.asInstanceOf[RVSBTypeInstPattern].imm(12, 1) ## BitPat.N(1)
        }
        if(op.isInstanceOf[RVUTypeInstPattern]) {
            return op.asInstanceOf[RVUTypeInstPattern].imm(31, 12) ## BitPat.N(12)
        }
        if(op.isInstanceOf[RVUJTypeInstPattern]) {
            return BitPat.N(11) ## op.asInstanceOf[RVUJTypeInstPattern].imm(20, 1) ## BitPat.N(1)
        }
        return BitPat.N(p(DATA_WIDTH))
    }
}

class Rs1Field(implicit p: Parameters) extends DecodeField[DecodePattern, UInt] 
{
    def name: String = "Rs1Field"
    def chiselType: UInt = UInt(p(AREG_WIDTH).W)
    def genTable(op: DecodePattern): BitPat = {
        if(op.isInstanceOf[RVRTypeInstPattern]) {
            return op.asInstanceOf[RVRTypeInstPattern].rs1
        }
        if(op.isInstanceOf[RVITypeInstPattern]) {
            return op.asInstanceOf[RVITypeInstPattern].rs1
        }
        if(op.isInstanceOf[RVSTypeInstPattern]) {
            return op.asInstanceOf[RVSTypeInstPattern].rs1
        }
        if(op.isInstanceOf[RVSBTypeInstPattern]) {
            return op.asInstanceOf[RVSBTypeInstPattern].rs1
        }
        return BitPat.N(p(AREG_WIDTH))
    }
}

class Rs2Field(implicit p: Parameters) extends DecodeField[DecodePattern, UInt] 
{
    def name: String = "Rs2Field"
    def chiselType: UInt = UInt(p(AREG_WIDTH).W)
    def genTable(op: DecodePattern): BitPat = {
        if(op.isInstanceOf[RVRTypeInstPattern]) {
            return op.asInstanceOf[RVRTypeInstPattern].rs2
        }        
        if(op.isInstanceOf[RVSTypeInstPattern]) {
            return op.asInstanceOf[RVSTypeInstPattern].rs2
        }
        if(op.isInstanceOf[RVSBTypeInstPattern]) {
            return op.asInstanceOf[RVSBTypeInstPattern].rs2
        }
        return BitPat.N(p(AREG_WIDTH))
    }
}

class RdField(implicit p: Parameters) extends DecodeField[DecodePattern, UInt] 
{
    def name: String = "RdField"
    def chiselType: UInt = UInt(p(AREG_WIDTH).W)
    def genTable(op: DecodePattern): BitPat = {
        if(op.isInstanceOf[RVRTypeInstPattern]) {
            return op.asInstanceOf[RVRTypeInstPattern].rd
        }        
        if(op.isInstanceOf[RVITypeInstPattern]) {
            return op.asInstanceOf[RVITypeInstPattern].rd
        }
        if(op.isInstanceOf[RVUTypeInstPattern]) {
            return op.asInstanceOf[RVUTypeInstPattern].rd
        }
        if(op.isInstanceOf[RVUJTypeInstPattern]) {
            return op.asInstanceOf[RVUJTypeInstPattern].rd
        }
        return BitPat.N(p(AREG_WIDTH))
    }
}

class OpcodeField(implicit p: Parameters) extends DecodeField[DecodePattern, UInt] {
    def name: String = "OpcodeField"
    def chiselType: UInt = UInt(7.W)
    def genTable(op: DecodePattern): BitPat = {
        if(op.isInstanceOf[RVRTypeInstPattern]) {
            return op.asInstanceOf[RVRTypeInstPattern].opcode
        }        
        if(op.isInstanceOf[RVITypeInstPattern]) {
            return op.asInstanceOf[RVITypeInstPattern].opcode
        }
        if(op.isInstanceOf[RVUTypeInstPattern]) {
            return op.asInstanceOf[RVUTypeInstPattern].opcode
        }
        if(op.isInstanceOf[RVUJTypeInstPattern]) {
            return op.asInstanceOf[RVUJTypeInstPattern].opcode
        }
        if(op.isInstanceOf[RVSTypeInstPattern]) {
            return op.asInstanceOf[RVSTypeInstPattern].opcode
        }
        if(op.isInstanceOf[RVSBTypeInstPattern]) {
            return op.asInstanceOf[RVSBTypeInstPattern].opcode
        }
        return BitPat.N(7)
    }
}

class Funct3Field(implicit p: Parameters) extends DecodeField[DecodePattern, UInt] {
    def name: String = "Funct3Field"
    def chiselType: UInt = UInt(3.W)
    def genTable(op: DecodePattern): BitPat = {
        if(op.isInstanceOf[RVRTypeInstPattern]) {
            return op.asInstanceOf[RVRTypeInstPattern].funct3
        }        
        if(op.isInstanceOf[RVITypeInstPattern]) {
            return op.asInstanceOf[RVITypeInstPattern].funct3
        }
        if(op.isInstanceOf[RVSTypeInstPattern]) {
            return op.asInstanceOf[RVSTypeInstPattern].funct3
        }
        if(op.isInstanceOf[RVSBTypeInstPattern]) {
            return op.asInstanceOf[RVSBTypeInstPattern].funct3
        }
        return BitPat.N(3)
    }
}

class Funct7Field(implicit p: Parameters) extends DecodeField[DecodePattern, UInt] {
    def name: String = "Funct7Field"
    def chiselType: UInt = UInt(7.W)
    def genTable(op: DecodePattern): BitPat = {
        if(op.isInstanceOf[RVRTypeInstPattern]) {
            return op.asInstanceOf[RVRTypeInstPattern].funct7
        }        
        return BitPat.N(7)
    }
}

class LatField(implicit p: Parameters) extends DecodeField[DecodePattern, UInt] {
    def name: String = "LatField"
    def chiselType: UInt = UInt(3.W)
    def genTable(op: DecodePattern): BitPat = {
        // to do
        return  BitPat.N(3)
    }
}

class HasRs1Field(implicit p: Parameters) extends DecodeField[DecodePattern, Bool] {
    def name: String = "HasRs1Field"
    def chiselType: Bool = Bool()
    def genTable(op: DecodePattern): BitPat = {
        if(op.isInstanceOf[RVRTypeInstPattern]) {
            return BitPat("b1")
        }        
        if(op.isInstanceOf[RVITypeInstPattern]) {
            return BitPat("b1")
        }
        if(op.isInstanceOf[RVSTypeInstPattern]) {
            return BitPat("b1")
        }
        if(op.isInstanceOf[RVSBTypeInstPattern]) {
            return BitPat("b1")
        }
        return BitPat("b0")
    }
}

class HasRs2Field(implicit p: Parameters) extends DecodeField[DecodePattern, Bool] {
    def name: String = "HasRs2Field"
    def chiselType: Bool = Bool()
    def genTable(op: DecodePattern): BitPat = {
        if(op.isInstanceOf[RVRTypeInstPattern]) {
            return BitPat("b1")
        }        
        if(op.isInstanceOf[RVSTypeInstPattern]) {
            return BitPat("b1")
        }
        if(op.isInstanceOf[RVSBTypeInstPattern]) {
            return BitPat("b1")
        }
        return BitPat("b0")
    }
}

class HasRdField(implicit p: Parameters) extends DecodeField[DecodePattern, Bool] {
    def name: String = "HasRdField"
    def chiselType: Bool = Bool()
    def genTable(op: DecodePattern): BitPat = {
        if(op.isInstanceOf[RVRTypeInstPattern]) {
            return BitPat("b1")
        }        
        if(op.isInstanceOf[RVITypeInstPattern]) {
            return BitPat("b1")
        }
        if(op.isInstanceOf[RVUTypeInstPattern]) {
            return BitPat("b1")
        }
        if(op.isInstanceOf[RVUJTypeInstPattern]) {
            return BitPat("b1")
        }
        return BitPat("b0")
    }
}

object OpcodeVal {
    val LUI: BitPat     = BitPat("b011 0111")
    val AUIPC: BitPat   = BitPat("b001 0111")

    val JAL: BitPat     = BitPat("b110 1111")

    val JALR: BitPat    = BitPat("b110 0111")

    val BEQ: BitPat     = BitPat("b110 0011")
    val BNE: BitPat     = BEQ
    val BLT: BitPat     = BEQ
    val BGE: BitPat     = BEQ
    val BLTU: BitPat    = BEQ
    val BGEU: BitPat    = BEQ

    val LB: BitPat = BitPat("b000 0011")
    val LH: BitPat = LB
    val LW: BitPat = LB
    val LBU: BitPat = LB
    val LHU: BitPat = LB

    val SB: BitPat = BitPat("b010 0011")
    val SH: BitPat = SB
    val SW: BitPat = SB

    val ADDI: BitPat = BitPat("b001 0011")
    val SLTI: BitPat = ADDI
    val SLTIU: BitPat = ADDI
    val XORI: BitPat = ADDI
    val ORI: BitPat = ADDI
    val ANDI: BitPat = ADDI
    val SLLI: BitPat = ADDI
    val SRLI: BitPat = ADDI
    val SRAI: BitPat = ADDI

    val R: BitPat = BitPat("b011 0011")

}

object Funct3Val {
    val JALR: BitPat = BitPat("b000")

    val BEQ: BitPat = BitPat("b000")
    val BNE: BitPat = BitPat("b001")
    val BLT: BitPat = BitPat("b100")
    val BGE: BitPat = BitPat("b101")
    val BLTU: BitPat = BitPat("b110")
    val BGEU: BitPat = BitPat("b111")

    val LB: BitPat = BitPat("b000")
    val LH: BitPat = BitPat("b001")
    val LW: BitPat = BitPat("b010")
    val LBU: BitPat = BitPat("b100")
    val LHU: BitPat = BitPat("b101")

    val SB: BitPat = BitPat("b000")
    val SH: BitPat = BitPat("b001")
    val SW: BitPat = BitPat("b010")

    val ADDI: BitPat = BitPat("b000")
    val SLTI: BitPat = BitPat("b010")
    val SLTIU: BitPat = BitPat("b011")
    val XORI: BitPat = BitPat("b100")
    val ORI: BitPat = BitPat("b110")
    val ANDI: BitPat = BitPat("b111")
    val SLLI: BitPat = BitPat("b001")
    val SRLI: BitPat = BitPat("b101")
    val SRAI: BitPat = BitPat("b101")

    val ADD: BitPat = BitPat("b000")
    val SUB: BitPat = ADD

    val SLL: BitPat = BitPat("b001")
    val SLT: BitPat = BitPat("b010")
    val SLTU: BitPat = BitPat("b011")
    val XOR: BitPat = BitPat("b100")
    val SRL: BitPat = BitPat("b101")
    val SRA: BitPat = SRL
    val OR: BitPat = BitPat("b110")
    val AND: BitPat = BitPat("b111")
}

object Funct7Val {
    val ADD: BitPat = BitPat("b000 0000")
    val SUB: BitPat = BitPat("b010 0000")
    val SLL: BitPat = ADD
    val SLT: BitPat = ADD
    val SLTU: BitPat = ADD
    val XOR: BitPat = ADD
    val SRL: BitPat = ADD
    val SRA: BitPat = SUB
    val OR: BitPat = ADD
    val AND: BitPat = ADD
}