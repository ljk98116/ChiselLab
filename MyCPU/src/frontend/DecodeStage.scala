package MyCPU.frontend

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._

import MyCPU.config._
import MyCPU.io._

class DecodeStage extends Module {
    val io = IO(new Bundle {
        val insts_i = Input(Vec(CPUConfig.FETCH_WIDTH, UInt(CPUConfig.ADDR_WIDTH.W)))
        val decodeRes_o = Output(Vec(CPUConfig.FETCH_WIDTH, new DecodeResultBundle))
    });

    val patterns: Seq[DecodePattern] = Seq(
        RVUTypeInstPattern(opcode = Opcode.LUI), 
        RVUTypeInstPattern(opcode = Opcode.AUIPC),
        RVUJTypeInstPattern(opcode = Opcode.JAL),
        RVITypeInstPattern(opcode = Opcode.JALR, funct3 = Funct3.JALR),
        RVSBTypeInstPattern(opcode = Opcode.BEQ, funct3 = Funct3.BEQ),
    );

    val fields = Seq(
        ImmField, Rs1Field, Rs2Field, RdField, OpcodeField, Funct3Field, Funct7Field
    )

    val decoderTable: DecodeTable[DecodePattern] = new DecodeTable(
        patterns,
        fields
    )

    var decodeRes = WireInit(VecInit(
        Seq.fill(CPUConfig.FETCH_WIDTH)(0.U.asTypeOf(new DecodeResultBundle))
    ))

    for(i <- 0 until CPUConfig.FETCH_WIDTH) {
        var decodeResult = decoderTable.decode(io.insts_i(i))

        decodeRes(i).rs1 := decodeResult(Rs1Field)
        decodeRes(i).rs2 := decodeResult(Rs2Field)
        decodeRes(i).imm := decodeResult(ImmField)
        decodeRes(i).rd := decodeResult(RdField)
        decodeRes(i).opcode := decodeResult(OpcodeField)
        decodeRes(i).funct3 := decodeResult(Funct3Field)
        decodeRes(i).funct7 := decodeResult(Funct7Field)
    }

    io.decodeRes_o := decodeRes
}