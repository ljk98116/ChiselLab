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

    var decodeRes = WireInit(VecInit(
        Seq.fill(p(FETCH_WIDTH))(0.U.asTypeOf(new DecodeResultBundle))
    ))

    for(i <- 0 until p(FETCH_WIDTH)) {
        when(instValids(i)) {
            decodeRes(i).opcode := insts(i)(6, 0)
            decodeRes(i).rd := insts(i)(11, 7)
            decodeRes(i).rs1 := insts(i)(19, 15)
            decodeRes(i).rs2 := insts(i)(24, 20)
            decodeRes(i).funct3 := insts(i)(14, 12)
            decodeRes(i).funct7 := insts(i)(31, 25)
            decodeRes(i).HasRd := false.B
            decodeRes(i).HasRs1 := false.B
            decodeRes(i).HasRs2 := false.B
            decodeRes(i).lat := 0.U
            decodeRes(i).valid := true.B
            decodeRes(i).pc := pc_vec(i)
            decodeRes(i).imm := 0.U

            // printf("pc: %x inst: %x opcode: %b rs1: %d rs2: %d rd: %d\n", pc_vec(i), insts(i), decodeRes(i).opcode, decodeRes(i).rs1, decodeRes(i).rs2, decodeRes(i).rd)

            switch(insts(i)(6, 0)) {
                is(OpcodeVal.R) {
                    decodeRes(i).HasRs1 := true.B
                    decodeRes(i).HasRs2 := true.B
                    decodeRes(i).HasRd := true.B
                }
                is(OpcodeVal.LB, OpcodeVal.ADDI, OpcodeVal.JALR, OpcodeVal.FENCEI) {
                    decodeRes(i).HasRs1 := true.B
                    decodeRes(i).HasRd := true.B
                    decodeRes(i).imm := Cat(Fill(20, insts(i)(31)), insts(i)(31, 20))                    
                }
                is(OpcodeVal.SB) {
                    decodeRes(i).HasRs1 := true.B
                    decodeRes(i).HasRs2 := true.B
                    decodeRes(i).imm := Cat(
                        Fill(20, insts(i)(31)), insts(i)(31, 25), insts(i)(11, 7)
                    )                    
                }
                is(OpcodeVal.BEQ) {
                    decodeRes(i).HasRs1 := true.B
                    decodeRes(i).HasRs2 := true.B 
                    decodeRes(i).imm := Cat(
                        Fill(19, insts(i)(31)), 
                        insts(i)(31), insts(i)(7), insts(i)(30, 25), 
                        insts(i)(11, 8), false.B
                    )
                }
                is(OpcodeVal.LUI, OpcodeVal.AUIPC) {
                    decodeRes(i).HasRd := true.B
                    decodeRes(i).imm := Cat(insts(i)(31, 12), 0.U(12.W))
                }
                is(OpcodeVal.JAL) {
                    decodeRes(i).HasRd := true.B
                    decodeRes(i).imm := Cat(
                        Fill(11, insts(i)(31)), insts(i)(31),
                        insts(i)(19, 12), insts(i)(20),
                        insts(i)(30, 21), false.B
                    )                    
                }
                is(OpcodeVal.CSR) {
                    decodeRes(i).HasRd := true.B
                    decodeRes(i).imm := Cat(Fill(20, false.B), insts(i)(31, 20))
                }
            }
        }.otherwise {
            decodeRes(i) := 0.U.asTypeOf(new DecodeResultBundle)
        }
    }

    io.decodeRes_o := decodeRes
    io.br_pred_info_vec := br_pred_info_vec
}