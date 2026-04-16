package MyCPU.config

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._
import org.chipsalliance.cde.config._

case class RVRTypeInstPattern (
    val opcode: BitPat,
    val funct3: BitPat,
    val funct7: BitPat,
    val rs2: BitPat = BitPat.dontCare(5),
    val rs1: BitPat = BitPat.dontCare(5),
    val rd: BitPat = BitPat.dontCare(5)
) extends DecodePattern {
    def bitPat: BitPat = funct7 ## rs2 ## rs1 ## funct3 ## rd ## opcode
}

case class RVITypeInstPattern(
    val opcode: BitPat,
    val funct3: BitPat,
    val imm: BitPat = BitPat.dontCare(32),
    val rs1: BitPat = BitPat.dontCare(5),
    val rd: BitPat = BitPat.dontCare(5)
) extends DecodePattern {
    def bitPat: BitPat = imm(11, 0) ## rs1 ## funct3 ## rd ## opcode
}

case class RVSTypeInstPattern(
    val opcode: BitPat,
    val funct3: BitPat,
    val imm: BitPat = BitPat.dontCare(32),
    val rs2: BitPat = BitPat.dontCare(5),
    val rs1: BitPat = BitPat.dontCare(5)
) extends DecodePattern {
    def bitPat: BitPat = imm(11, 5) ## rs2 ## rs1 ## funct3 ## imm(4, 0) ## opcode
}

case class RVSBTypeInstPattern(
    val opcode: BitPat,
    val funct3: BitPat,
    val imm: BitPat = BitPat.dontCare(32),
    val rs2: BitPat = BitPat.dontCare(5),
    val rs1: BitPat = BitPat.dontCare(5)
) extends DecodePattern {
    def bitPat: BitPat = imm(12) ## imm(10, 5) ## rs2 ## rs1 ## funct3 ## imm(4, 1) ## imm(11) ## opcode
}

case class RVUTypeInstPattern(
    val opcode: BitPat,
    val imm: BitPat = BitPat.dontCare(32),
    val rd: BitPat = BitPat.dontCare(5)
) extends DecodePattern {
    def bitPat: BitPat = imm(31, 12) ## rd ## opcode
}

case class RVUJTypeInstPattern(
    val opcode: BitPat,
    val imm: BitPat = BitPat.dontCare(32),
    val rd: BitPat = BitPat.dontCare(5)
) extends DecodePattern {
    def bitPat: BitPat = imm(20) ## imm(10, 1) ## imm(11) ## imm(19, 12) ## rd ## opcode
}

// -------------------- DecodeField classes --------------------
class RVITypeImmField(implicit p: Parameters) extends DecodeField[RVITypeInstPattern, UInt] {
  def name: String = "RVITypeImmField"
  def chiselType: UInt = UInt(p(DATA_WIDTH).W)
  def genTable(op: RVITypeInstPattern): BitPat = BitPat.N(20) ## op.imm(11, 0)
}

class RVSTypeImmField(implicit p: Parameters) extends DecodeField[RVSTypeInstPattern, UInt] {
  def name: String = "RVSTypeImmField"
  def chiselType: UInt = UInt(p(DATA_WIDTH).W)
  def genTable(op: RVSTypeInstPattern): BitPat = BitPat.N(20) ## op.imm(11, 0)
}

class RVSBTypeImmField(implicit p: Parameters) extends DecodeField[RVSBTypeInstPattern, UInt] {
  def name: String = "RVSBTypeImmField"
  def chiselType: UInt = UInt(p(DATA_WIDTH).W)
  def genTable(op: RVSBTypeInstPattern): BitPat = BitPat.N(19) ## op.imm(12, 1) ## BitPat.N(1)
}

class RVUTypeImmField(implicit p: Parameters) extends DecodeField[RVUTypeInstPattern, UInt] {
  def name: String = "RVUTypeImmField"
  def chiselType: UInt = UInt(p(DATA_WIDTH).W)
  def genTable(op: RVUTypeInstPattern): BitPat = op.imm(31, 12) ## BitPat.N(12)
}

class RVUJTypeImmField(implicit p: Parameters) extends DecodeField[RVUJTypeInstPattern, UInt] {
  def name: String = "RVUJTypeImmField"
  def chiselType: UInt = UInt(p(DATA_WIDTH).W)
  def genTable(op: RVUJTypeInstPattern): BitPat = BitPat.N(11) ## op.imm(20, 1) ## BitPat.N(1)
}


class Rs1Field(implicit p: Parameters) extends DecodeField[DecodePattern, UInt] {
  def name: String = "Rs1Field"
  def chiselType: UInt = UInt(p(AREG_WIDTH).W)
  def genTable(op: DecodePattern): BitPat = op match {
    case rvr: RVRTypeInstPattern => rvr.rs1
    case rvi: RVITypeInstPattern => rvi.rs1
    case rvs: RVSTypeInstPattern => rvs.rs1
    case rvb: RVSBTypeInstPattern => rvb.rs1
    case _ => BitPat.N(p(AREG_WIDTH))
  }
}

class Rs2Field(implicit p: Parameters) extends DecodeField[DecodePattern, UInt] {
  def name: String = "Rs2Field"
  def chiselType: UInt = UInt(p(AREG_WIDTH).W)
  def genTable(op: DecodePattern): BitPat = op match {
    case rvr: RVRTypeInstPattern => rvr.rs2
    case rvs: RVSTypeInstPattern => rvs.rs2
    case rvb: RVSBTypeInstPattern => rvb.rs2
    case _ => BitPat.N(p(AREG_WIDTH))
  }
}

class RdField(implicit p: Parameters) extends DecodeField[DecodePattern, UInt] {
  def name: String = "RdField"
  def chiselType: UInt = UInt(p(AREG_WIDTH).W)
  def genTable(op: DecodePattern): BitPat = op match {
    case rvr: RVRTypeInstPattern => rvr.rd
    case rvi: RVITypeInstPattern => rvi.rd
    case rvu: RVUTypeInstPattern => rvu.rd
    case rvj: RVUJTypeInstPattern => rvj.rd
    case _ => BitPat.N(p(AREG_WIDTH))
  }
}

class OpcodeField(implicit p: Parameters) extends DecodeField[DecodePattern, UInt] {
  def name: String = "OpcodeField"
  def chiselType: UInt = UInt(7.W)
  def genTable(op: DecodePattern): BitPat = op match {
    case r: RVRTypeInstPattern => r.opcode
    case i: RVITypeInstPattern => i.opcode
    case u: RVUTypeInstPattern => u.opcode
    case j: RVUJTypeInstPattern => j.opcode
    case s: RVSTypeInstPattern => s.opcode
    case b: RVSBTypeInstPattern => b.opcode
    case _ => BitPat.N(7)
  }
}

class Funct3Field(implicit p: Parameters) extends DecodeField[DecodePattern, UInt] {
  def name: String = "Funct3Field"
  def chiselType: UInt = UInt(3.W)
  def genTable(op: DecodePattern): BitPat = op match {
    case r: RVRTypeInstPattern => r.funct3
    case i: RVITypeInstPattern => i.funct3
    case s: RVSTypeInstPattern => s.funct3
    case b: RVSBTypeInstPattern => b.funct3
    case _ => BitPat.N(3)
  }
}

class Funct7Field(implicit p: Parameters) extends DecodeField[DecodePattern, UInt] {
  def name: String = "Funct7Field"
  def chiselType: UInt = UInt(7.W)
  def genTable(op: DecodePattern): BitPat = op match {
    case r: RVRTypeInstPattern => r.funct7
    case _ => BitPat.N(7)
  }
}

class LatField(implicit p: Parameters) extends DecodeField[DecodePattern, UInt] {
  def name: String = "LatField"
  def chiselType: UInt = UInt(3.W)
  def genTable(op: DecodePattern): BitPat = BitPat.N(3) // 可后续改为按指令类型设置
}

class HasRs1Field(implicit p: Parameters) extends DecodeField[DecodePattern, Bool] {
  def name: String = "HasRs1Field"
  def chiselType: Bool = Bool()
  def genTable(op: DecodePattern): BitPat = op match {
    case _: RVRTypeInstPattern => BitPat("b1")
    case _: RVITypeInstPattern => BitPat("b1")
    case _: RVSTypeInstPattern => BitPat("b1")
    case _: RVSBTypeInstPattern => BitPat("b1")
    case _ => BitPat("b0")
  }
}

class HasRs2Field(implicit p: Parameters) extends DecodeField[DecodePattern, Bool] {
  def name: String = "HasRs2Field"
  def chiselType: Bool = Bool()
  def genTable(op: DecodePattern): BitPat = op match {
    case _: RVRTypeInstPattern => BitPat("b1")
    case _: RVSTypeInstPattern => BitPat("b1")
    case _: RVSBTypeInstPattern => BitPat("b1")
    case _ => BitPat("b0")
  }
}

class HasRdField(implicit p: Parameters) extends DecodeField[DecodePattern, Bool] {
  def name: String = "HasRdField"
  def chiselType: Bool = Bool()
  def genTable(op: DecodePattern): BitPat = op match {
    case _: RVRTypeInstPattern => BitPat("b1")
    case _: RVITypeInstPattern => BitPat("b1")
    case _: RVUTypeInstPattern => BitPat("b1")
    case _: RVUJTypeInstPattern => BitPat("b1")
    case _ => BitPat("b0")
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