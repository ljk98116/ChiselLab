package MyCPU.frontend

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._

import MyCPU.config._
import MyCPU.io._

class DecodeStage extends Module {
    val io = IO(new Bundle {
        val insts_i = Input(Vec(CPUConfig.FETCH_WIDTH, UInt(CPUConfig.ADDR_WIDTH.W)))
        val decodeRes_o = Output(Vec(CPUConfig.FETCH_WIDTH, new DecodeResBundle))
    });
 
    
    val patterns: Seq[DecodePattern] = Seq(
        RVUTypeInstPattern(opcode = Opcode.LUI), 
        RVUTypeInstPattern(opcode = Opcode.AUIPC),
        RVUJTypeInstPattern(opcode = Opcode.JAL),
        RVITypeInstPattern(opcode = Opcode.JALR, funct3 = Funct3.JALR),
        RVSBTypeInstPattern(opcode = Opcode.BEQ, funct3 = Funct3.BEQ),
    );

    val fields = Seq(
        Imm, Rs1, Rs2, Rd,
    )

    val decoderTable: DecodeTable[DecodePattern] = new DecodeTable(
        patterns,
        fields
    )

    var decodeResult = decoderTable.decode(0.U)
    var imm = decodeResult(Imm)
}