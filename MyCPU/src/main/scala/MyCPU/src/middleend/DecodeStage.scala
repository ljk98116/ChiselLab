package MyCPU.middleend;
import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._

import org.chipsalliance.cde.config._
import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

// generate redirect for direct jump inst if get jump error
class DecodeStage(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val flush = Input(Bool())
        val stall = Input(Bool())
        // from if5 stage
        val if5_pc_vec_i = Input(Vec(p(FETCH_WIDTH), UInt(p(ADDR_WIDTH).W)))
        val if5_inst_i = Input(Vec(p(FETCH_WIDTH), UInt(p(DATA_WIDTH).W)))
        val if5_instValid_i = Input(Vec(p(FETCH_WIDTH), Bool()))
        val if5_br_pred_info_vec_i = Input(Vec(p(FETCH_WIDTH), new BranchPredInfo))
        // to rename stage
        val decodeRes_o = Output(Vec(p(FETCH_WIDTH), new DecodeResultBundle))
        val br_pred_info_vec = Output(Vec(p(FETCH_WIDTH), new BranchPredInfo))
    })

    val pc_vec = PipeReg(io.if5_pc_vec_i, io.stall, io.flush)
    val insts = PipeReg(io.if5_inst_i, io.stall, io.flush)
    val instValids = PipeReg(io.if5_instValid_i, io.stall, io.flush)
    val br_pred_info_vec = PipeReg(io.if5_br_pred_info_vec_i, io.stall, io.flush)

    val patterns: Seq[DecodePattern] = Seq(
        
        RVUTypeInstPattern(opcode = OpcodeVal.LUI), 
        RVUTypeInstPattern(opcode = OpcodeVal.AUIPC),
        RVUJTypeInstPattern(opcode = OpcodeVal.JAL),
        RVITypeInstPattern(
            opcode = OpcodeVal.JALR, funct3 = Funct3Val.JALR
        ),
        RVSBTypeInstPattern(
            opcode = OpcodeVal.BEQ, funct3 = Funct3Val.BEQ
        ),
        RVSBTypeInstPattern(
            opcode = OpcodeVal.BNE, funct3 = Funct3Val.BNE
        ),
        RVSBTypeInstPattern(
            opcode = OpcodeVal.BLT, funct3 = Funct3Val.BLT
        ),
        RVSBTypeInstPattern(
            opcode = OpcodeVal.BGE, funct3 = Funct3Val.BGE
        ),
        RVSBTypeInstPattern(
            opcode = OpcodeVal.BLTU, funct3 = Funct3Val.BLTU
        ),
        RVSBTypeInstPattern(
            opcode = OpcodeVal.BGEU, funct3 = Funct3Val.BGEU
        ),
        RVITypeInstPattern(
            opcode = OpcodeVal.LB, funct3 = Funct3Val.LB
        ),
        RVITypeInstPattern(
            opcode = OpcodeVal.LH, funct3 = Funct3Val.LH
        ),
        RVITypeInstPattern(
            opcode = OpcodeVal.LW, funct3 = Funct3Val.LW
        ),
        RVITypeInstPattern(
            opcode = OpcodeVal.LBU, funct3 = Funct3Val.LBU
        ),
        RVITypeInstPattern(
            opcode = OpcodeVal.LHU, funct3 = Funct3Val.LHU
        ),                
        RVSTypeInstPattern(
            opcode = OpcodeVal.SB, funct3 = Funct3Val.SB
        ),
        RVSTypeInstPattern(
            opcode = OpcodeVal.SH, funct3 = Funct3Val.SH
        ),
        RVSTypeInstPattern(
            opcode = OpcodeVal.SW, funct3 = Funct3Val.SW
        ),
        RVITypeInstPattern(
            opcode = OpcodeVal.ADDI, funct3 = Funct3Val.ADDI
        ),
        RVITypeInstPattern(
            opcode = OpcodeVal.SLTI, funct3 = Funct3Val.SLTI
        ),
        RVITypeInstPattern(
            opcode = OpcodeVal.SLTIU, funct3 = Funct3Val.SLTIU
        ),
        RVITypeInstPattern(
            opcode = OpcodeVal.XORI, funct3 = Funct3Val.XORI
        ),
        RVITypeInstPattern(
            opcode = OpcodeVal.ORI, funct3 = Funct3Val.ORI
        ),
        RVITypeInstPattern(
            opcode = OpcodeVal.ANDI, funct3 = Funct3Val.ANDI
        ),
        RVITypeInstPattern(
            opcode = OpcodeVal.SLLI, funct3 = Funct3Val.SLLI
        ),
        RVITypeInstPattern(
            opcode = OpcodeVal.SRLI, funct3 = Funct3Val.SRLI
        ),
        RVITypeInstPattern(
            opcode = OpcodeVal.SRAI, funct3 = Funct3Val.SRAI
        ),        
        RVRTypeInstPattern(
            opcode = OpcodeVal.R, funct3 = Funct3Val.ADD, funct7 = Funct7Val.ADD
        ),
        RVRTypeInstPattern(
            opcode = OpcodeVal.R, funct3 = Funct3Val.SUB, funct7 = Funct7Val.SUB
        ),
        RVRTypeInstPattern(
            opcode = OpcodeVal.R, funct3 = Funct3Val.SLL, funct7 = Funct7Val.SLL
        ),
        RVRTypeInstPattern(
            opcode = OpcodeVal.R, funct3 = Funct3Val.SLT, funct7 = Funct7Val.SLT
        ),
        RVRTypeInstPattern(
            opcode = OpcodeVal.R, funct3 = Funct3Val.SLTU, funct7 = Funct7Val.SLTU
        ),
        RVRTypeInstPattern(
            opcode = OpcodeVal.R, funct3 = Funct3Val.XOR, funct7 = Funct7Val.XOR
        ),
        RVRTypeInstPattern(
            opcode = OpcodeVal.R, funct3 = Funct3Val.SRL, funct7 = Funct7Val.SRL
        ),
        RVRTypeInstPattern(
            opcode = OpcodeVal.R, funct3 = Funct3Val.SRA, funct7 = Funct7Val.SRA
        ),
        RVRTypeInstPattern(
            opcode = OpcodeVal.R, funct3 = Funct3Val.OR, funct7 = Funct7Val.OR
        ),
        RVRTypeInstPattern(
            opcode = OpcodeVal.R, funct3 = Funct3Val.AND, funct7 = Funct7Val.AND
        ),
    )

    val Imm: ImmField = new ImmField
    val Rs1: Rs1Field = new Rs1Field
    val Rs2: Rs2Field = new Rs2Field
    val Rd: RdField = new RdField
    val Opcode: OpcodeField = new OpcodeField
    val Funct3: Funct3Field = new Funct3Field
    val Funct7: Funct7Field = new Funct7Field
    val Lat: LatField = new LatField
    val HasRs1: HasRs1Field = new HasRs1Field
    val HasRs2: HasRs2Field = new HasRs2Field
    val HasRd: HasRdField = new HasRdField

    val fields = Seq(
        Imm, Rs1, Rs2, Rd, Opcode, Funct3, Funct7, Lat, HasRs1, HasRs2, HasRd
    )

    val decoderTable: DecodeTable[DecodePattern] = new DecodeTable(
        patterns,
        fields
    )

    var decodeRes = WireInit(VecInit(
        Seq.fill(p(FETCH_WIDTH))(0.U.asTypeOf(new DecodeResultBundle))
    ))

    for(i <- 0 until p(FETCH_WIDTH)) {
        when(instValids(i)) {
            var decodeResult = decoderTable.decode(insts(i))
            decodeRes(i).rs1 := decodeResult(Rs1)
            decodeRes(i).rs2 := decodeResult(Rs2)
            decodeRes(i).imm := decodeResult(Imm)
            decodeRes(i).rd := decodeResult(Rd)
            decodeRes(i).opcode := decodeResult(Opcode)
            decodeRes(i).funct3 := decodeResult(Funct3)
            decodeRes(i).funct7 := decodeResult(Funct7)
            decodeRes(i).lat := decodeResult(Lat)
            decodeRes(i).valid := true.B
            decodeRes(i).pc := pc_vec(i)
            decodeRes(i).HasRs1 := decodeResult(HasRs1)
            decodeRes(i).HasRs2 := decodeResult(HasRs2)
            decodeRes(i).HasRd := decodeResult(HasRd)
        }.otherwise {
            decodeRes(i) := 0.U.asTypeOf(new DecodeResultBundle)
        }
    }

    io.decodeRes_o := decodeRes
    io.br_pred_info_vec := br_pred_info_vec
}