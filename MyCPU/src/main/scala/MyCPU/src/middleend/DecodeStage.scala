package MyCPU.middleend;
import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._

import org.chipsalliance.cde.config._
import MyCPU.config._
import MyCPU.io._

class DecodeStage(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val insts_i = Input(Vec(p(FETCH_WIDTH), UInt(p(ADDR_WIDTH).W)))
        val decodeRes_o = Output(Vec(p(FETCH_WIDTH), new DecodeResultBundle))
    });

    val patterns: Seq[DecodePattern] = Seq(
        RVUTypeInstPattern(opcode = OpcodeVal.LUI), 
        RVUTypeInstPattern(opcode = OpcodeVal.AUIPC),
        RVUJTypeInstPattern(opcode = OpcodeVal.JAL),
        RVITypeInstPattern(opcode = OpcodeVal.JALR, funct3 = Funct3Val.JALR),
        RVSBTypeInstPattern(opcode = OpcodeVal.BEQ, funct3 = Funct3Val.BEQ),
    );

    val Imm: ImmField = new ImmField
    val Rs1: Rs1Field = new Rs1Field
    val Rs2: Rs2Field = new Rs2Field
    val Rd: RdField = new RdField
    val Opcode: OpcodeField = new OpcodeField
    val Funct3: Funct3Field = new Funct3Field
    val Funct7: Funct7Field = new Funct7Field
    val Lat: LatField = new LatField

    val fields = Seq(
        Imm, Rs1, Rs2, Rd, Opcode, Funct3, Funct7, Lat
    )

    val decoderTable: DecodeTable[DecodePattern] = new DecodeTable(
        patterns,
        fields
    )

    var decodeRes = WireInit(VecInit(
        Seq.fill(p(FETCH_WIDTH))(0.U.asTypeOf(new DecodeResultBundle))
    ))

    for(i <- 0 until p(FETCH_WIDTH)) {
        var decodeResult = decoderTable.decode(io.insts_i(i))

        decodeRes(i).rs1 := decodeResult(Rs1)
        decodeRes(i).rs2 := decodeResult(Rs2)
        decodeRes(i).imm := decodeResult(Imm)
        decodeRes(i).rd := decodeResult(Rd)
        decodeRes(i).opcode := decodeResult(Opcode)
        decodeRes(i).funct3 := decodeResult(Funct3)
        decodeRes(i).funct7 := decodeResult(Funct7)
    }

    io.decodeRes_o := decodeRes
}