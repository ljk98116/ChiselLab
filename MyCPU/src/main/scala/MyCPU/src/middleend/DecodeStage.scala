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

    val RVRTypePatterns: Seq[RVRTypeInstPattern] = Seq(
        RVRTypeInstPattern(
            OpcodeVal.R, Funct3Val.ADD, funct7 = Funct7Val.ADD
        ),
        RVRTypeInstPattern(
            OpcodeVal.R, Funct3Val.SUB, funct7 = Funct7Val.SUB
        ),
        RVRTypeInstPattern(
            OpcodeVal.R, Funct3Val.SLL, funct7 = Funct7Val.SLL
        ),
        RVRTypeInstPattern(
            OpcodeVal.R, Funct3Val.SLT, funct7 = Funct7Val.SLT
        ),
        RVRTypeInstPattern(
            OpcodeVal.R, Funct3Val.SLTU, funct7 = Funct7Val.SLTU
        ),
        RVRTypeInstPattern(
            OpcodeVal.R, Funct3Val.XOR, funct7 = Funct7Val.XOR
        ),
        RVRTypeInstPattern(
            OpcodeVal.R, Funct3Val.SRL, funct7 = Funct7Val.SRL
        ),
        RVRTypeInstPattern(
            OpcodeVal.R, Funct3Val.SRA, funct7 = Funct7Val.SRA
        ),
        RVRTypeInstPattern(
            OpcodeVal.R, Funct3Val.OR, funct7 = Funct7Val.OR
        ),
        RVRTypeInstPattern(
            OpcodeVal.R, Funct3Val.AND, funct7 = Funct7Val.AND
        )
    )
    val RVUTypePatterns: Seq[RVUTypeInstPattern] = Seq(
        RVUTypeInstPattern(OpcodeVal.LUI), 
        RVUTypeInstPattern(OpcodeVal.AUIPC)
    )
    val RVUJTypePatterns: Seq[RVUJTypeInstPattern] = Seq(
        RVUJTypeInstPattern(OpcodeVal.JAL)
    )

    val RVITypePatterns: Seq[RVITypeInstPattern] = Seq(
        RVITypeInstPattern(
            OpcodeVal.JALR, Funct3Val.JALR
        ),
        RVITypeInstPattern(
            OpcodeVal.ADDI, Funct3Val.ADDI
        ),
        RVITypeInstPattern(
            OpcodeVal.SLTI, Funct3Val.SLTI
        ),
        RVITypeInstPattern(
            OpcodeVal.SLTIU, Funct3Val.SLTIU
        ),
        RVITypeInstPattern(
            OpcodeVal.XORI, Funct3Val.XORI
        ),
        RVITypeInstPattern(
            OpcodeVal.ORI, Funct3Val.ORI
        ),
        RVITypeInstPattern(
            OpcodeVal.ANDI, Funct3Val.ANDI
        ),
        RVITypeInstPattern(
            OpcodeVal.SLLI, Funct3Val.SLLI
        ),
        RVITypeInstPattern(
            OpcodeVal.SRLI, Funct3Val.SRLI
        ),
        RVITypeInstPattern(
            OpcodeVal.SRAI, Funct3Val.SRAI
        ),
        RVITypeInstPattern(
            OpcodeVal.LB, Funct3Val.LB
        ),
        RVITypeInstPattern(
            OpcodeVal.LH, Funct3Val.LH
        ),
        RVITypeInstPattern(
            OpcodeVal.LW, Funct3Val.LW
        ),
        RVITypeInstPattern(
            OpcodeVal.LBU, Funct3Val.LBU
        ),
        RVITypeInstPattern(
            OpcodeVal.LHU, Funct3Val.LHU
        ),
    )
    val RVSBTypePatterns: Seq[RVSBTypeInstPattern] = Seq(
        RVSBTypeInstPattern(
            OpcodeVal.BEQ, Funct3Val.BEQ
        ),
        RVSBTypeInstPattern(
            OpcodeVal.BNE, Funct3Val.BNE
        ),
        RVSBTypeInstPattern(
            OpcodeVal.BLT, Funct3Val.BLT
        ),
        RVSBTypeInstPattern(
            OpcodeVal.BGE, Funct3Val.BGE
        ),
        RVSBTypeInstPattern(
            OpcodeVal.BLTU, Funct3Val.BLTU
        ),
        RVSBTypeInstPattern(
            OpcodeVal.BGEU, Funct3Val.BGEU
        ),
    )

    val RVSTypePatterns: Seq[RVSTypeInstPattern] = Seq(
        RVSTypeInstPattern(
            OpcodeVal.SB, Funct3Val.SB
        ),
        RVSTypeInstPattern(
            OpcodeVal.SH, Funct3Val.SH
        ),
        RVSTypeInstPattern(
            OpcodeVal.SW, Funct3Val.SW
        )
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

    println(Imm.genTable(patterns(0)))

    for(i <- 0 until p(FETCH_WIDTH)) {
        when(instValids(i)) {
            var decodeResult = decoderTable.decode(insts(i))
            printf(p"inst: ${insts(i)}\n")
            printf(p"decodeResult: $decodeResult \n")
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