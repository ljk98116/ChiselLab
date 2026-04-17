package MyCPU.config

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._
import org.chipsalliance.cde.config._

object OpcodeVal {
    val LUI     = "b0110111".U
    val AUIPC   = "b0010111".U

    val JAL     = "b1101111".U

    val JALR    = "b1100111".U

    val BEQ     = "b1100011".U
    val BNE     = BEQ
    val BLT     = BEQ
    val BGE     = BEQ
    val BLTU    = BEQ
    val BGEU    = BEQ

    val LB = "b0000011".U
    val LH = LB
    val LW = LB
    val LBU = LB
    val LHU = LB

    val SB = "b0100011".U
    val SH = SB
    val SW = SB

    val ADDI = "b0010011".U
    val SLTI = ADDI
    val SLTIU = ADDI
    val XORI = ADDI
    val ORI = ADDI
    val ANDI = ADDI
    val SLLI = ADDI
    val SRLI = ADDI
    val SRAI = ADDI

    val R = "b0110011".U

}

object Funct3Val {
    val JALR = "b000".U

    val BEQ = "b000".U
    val BNE = "b001".U
    val BLT = "b100".U
    val BGE = "b101".U
    val BLTU = "b110".U
    val BGEU = "b111".U

    val LB = "b000".U
    val LH = "b001".U
    val LW = "b010".U
    val LBU = "b100".U
    val LHU = "b101".U

    val SB = "b000".U
    val SH = "b001".U
    val SW = "b010".U

    val ADDI = "b000".U
    val SLTI = "b010".U
    val SLTIU = "b011".U
    val XORI = "b100".U
    val ORI = "b110".U
    val ANDI = "b111".U
    val SLLI = "b001".U
    val SRLI = "b101".U
    val SRAI = "b101".U

    val ADD = "b000".U
    val SUB = ADD

    val SLL = "b001".U
    val SLT = "b010".U
    val SLTU = "b011".U
    val XOR = "b100".U
    val SRL = "b101".U
    val SRA = SRL
    val OR = "b110".U
    val AND = "b111".U
}

object Funct7Val {
    val ADD = "b0000000".U
    val SUB = "b0100000".U
    val SLL = ADD
    val SLT = ADD
    val SLTU = ADD
    val XOR = ADD
    val SRL = ADD
    val SRA = SUB
    val OR = ADD
    val AND = ADD
}