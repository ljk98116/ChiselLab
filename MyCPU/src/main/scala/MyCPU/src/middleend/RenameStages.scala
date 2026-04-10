package MyCPU.middleend

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._

import org.chipsalliance.cde.config._
import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

class RenameStages(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val stall_from_backpipe = Input(Bool())
        val flush = Input(Bool())
        // from if5
        val decodeRes = Input(Vec(p(FETCH_WIDTH), new DecodeResultBundle))
        val br_pred_info_vec = Input(Vec(p(FETCH_WIDTH), new BranchPredInfo))
        // to RATSpec(rename1)
        val rs1_rat_read_req = Output(Vec(p(FETCH_WIDTH), new RATSpecReadReq))
        val rs2_rat_read_req = Output(Vec(p(FETCH_WIDTH), new RATSpecReadReq))
        val rd_rat_read_req = Output(Vec(p(FETCH_WIDTH), new RATSpecReadReq))
        val rd_rat_write_req = Output(Vec(p(FETCH_WIDTH), new RATSpecWriteReq))
        // from RATSpec(rename1)
        val rs1_rat_read_resp = Input(Vec(p(FETCH_WIDTH), new RATSpecReadResp))
        val rs2_rat_read_resp = Input(Vec(p(FETCH_WIDTH), new RATSpecReadResp))
        val rd_rat_read_resp = Input(Vec(p(FETCH_WIDTH), new RATSpecReadResp))
        // freeRegIdBuffer io
        val freeRegBufferReadReq = Output(Vec(p(FETCH_WIDTH), new FreeRegIdBufferReadReq))
        val freeRegBufferReadResp = Input(Vec(p(FETCH_WIDTH), new FreeRegIdBufferReadResp))
        // to dispatch
        val renameRes = Output(Vec(p(FETCH_WIDTH), new uOp))
        val rename2_stall = Output(Bool())
    })

    val rename1_decodeRes = PipeReg(io.decodeRes, io.stall_from_backpipe, io.flush)
    val rename1_br_pred_info_vec = PipeReg(io.br_pred_info_vec, io.stall_from_backpipe, io.flush)

    // i th rs1 is same as j(j < i) th rd ?
    val rename1_rs1_raw_masks = WireInit(VecInit(
        Seq.fill(p(FETCH_WIDTH))(
            VecInit(Seq.fill(p(FETCH_WIDTH))(false.B))
        )
    ))

    val rename1_rs2_raw_masks = WireInit(VecInit(
        Seq.fill(p(FETCH_WIDTH))(
            VecInit(Seq.fill(p(FETCH_WIDTH))(false.B))
        )
    ))

    // judge write RATSpec or not
    val rename1_waw_masks_after = WireInit(VecInit(
        Seq.fill(p(FETCH_WIDTH))(
            VecInit(Seq.fill(p(FETCH_WIDTH))(false.B))
        )
    ))

    // judge old pd value
    val rename1_waw_masks_before = WireInit(VecInit(
        Seq.fill(p(FETCH_WIDTH))(
            VecInit(Seq.fill(p(FETCH_WIDTH))(false.B))
        )
    ))

    val rename1_rs1_raw_idx = WireInit(VecInit(
        Seq.fill(p(FETCH_WIDTH))(0.U(log2Ceil(p(FETCH_WIDTH)).W))
    ))

    val rename1_rs2_raw_idx = WireInit(VecInit(
        Seq.fill(p(FETCH_WIDTH))(0.U(log2Ceil(p(FETCH_WIDTH)).W))
    ))

    val rename1_waw_before_idx = WireInit(VecInit(
        Seq.fill(p(FETCH_WIDTH))(0.U(log2Ceil(p(FETCH_WIDTH)).W))
    ))

    val rename1_rs1_has_raw = WireInit(VecInit.fill(p(FETCH_WIDTH))(false.B))
    val rename1_rs2_has_raw = WireInit(VecInit.fill(p(FETCH_WIDTH))(false.B))
    val rename1_has_waw_before = WireInit(VecInit.fill(p(FETCH_WIDTH))(false.B))
    val rename1_has_waw_after = WireInit(VecInit.fill(p(FETCH_WIDTH))(false.B))

    for(i <- 0 until p(FETCH_WIDTH)) {
        for(j <- 0 until i) {
            rename1_rs1_raw_masks(i)(j) := rename1_decodeRes(i).HasRs1 && rename1_decodeRes(j).HasRd && rename1_decodeRes(i).rs1 === rename1_decodeRes(j).rd
            rename1_rs2_raw_masks(i)(j) := rename1_decodeRes(i).HasRs2 && rename1_decodeRes(j).HasRd && rename1_decodeRes(i).rs2 === rename1_decodeRes(j).rd
            rename1_waw_masks_before(i)(j) := rename1_decodeRes(i).HasRd && rename1_decodeRes(j).HasRd && rename1_decodeRes(i).rd === rename1_decodeRes(j).rd
        }

        for(j <- i + 1 until p(FETCH_WIDTH)) {
            rename1_waw_masks_after(i)(j) := rename1_decodeRes(i).HasRd && rename1_decodeRes(j).HasRd && rename1_decodeRes(i).rd === rename1_decodeRes(j).rd
        }

        rename1_rs1_raw_idx(i) := (p(FETCH_WIDTH) - 1).U - PriorityEncoder(rename1_rs1_raw_masks(i).reverse)
        rename1_rs2_raw_idx(i) := (p(FETCH_WIDTH) - 1).U - PriorityEncoder(rename1_rs2_raw_masks(i).reverse)
        rename1_waw_before_idx(i) := (p(FETCH_WIDTH) - 1).U - PriorityEncoder(rename1_waw_masks_before(i).reverse)

        rename1_rs1_has_raw(i) := rename1_rs1_raw_masks(i).reduce(_ || _)
        rename1_rs2_has_raw(i) := rename1_rs2_raw_masks(i).reduce(_ || _)
        rename1_has_waw_before(i) := rename1_waw_masks_before(i).reduce(_ || _)
        rename1_has_waw_after(i) := rename1_waw_masks_after(i).reduce(_ || _)
    }

    /// rename2 judge raw hazard with rename1 result, and generate rename2 result
    val rename2_decodeRes = PipeReg(rename1_decodeRes, io.stall_from_backpipe, io.flush)
    val rename2_br_pred_info_vec = PipeReg(rename1_br_pred_info_vec, io.stall_from_backpipe, io.flush)

    val rename2_rs1_raw_idx = PipeReg(rename1_rs1_raw_idx, io.stall_from_backpipe, io.flush)
    val rename2_rs2_raw_idx = PipeReg(rename1_rs2_raw_idx, io.stall_from_backpipe, io.flush)
    val rename2_waw_before_idx = PipeReg(rename1_waw_before_idx, io.stall_from_backpipe, io.flush)

    val rename2_rs1_has_raw = PipeReg(rename1_rs1_has_raw, io.stall_from_backpipe, io.flush)
    val rename2_rs2_has_raw = PipeReg(rename1_rs2_has_raw, io.stall_from_backpipe, io.flush)
    val rename2_has_waw_before = PipeReg(rename1_has_waw_before, io.stall_from_backpipe, io.flush)
    val rename2_has_waw_after = PipeReg(rename1_has_waw_after, io.stall_from_backpipe, io.flush)

    for(i <- 0 until p(FETCH_WIDTH)) {
        io.rs1_rat_read_req(i).valid := rename2_decodeRes(i).valid && rename2_decodeRes(i).HasRs1 && !rename2_rs1_has_raw(i)
        io.rs1_rat_read_req(i).areg := rename2_decodeRes(i).rs1

        io.rs2_rat_read_req(i).valid := rename2_decodeRes(i).valid && rename2_decodeRes(i).HasRs2 && !rename2_rs2_has_raw(i)
        io.rs2_rat_read_req(i).areg := rename2_decodeRes(i).rs2

        io.rd_rat_read_req(i).valid := rename2_decodeRes(i).valid && rename2_decodeRes(i).HasRd && !rename2_has_waw_before(i)
        io.rd_rat_read_req(i).areg := rename2_decodeRes(i).rd

        io.rd_rat_write_req(i).valid := 
            rename2_decodeRes(i).valid && rename2_decodeRes(i).HasRd && 
            !rename2_has_waw_after(i) && io.freeRegBufferReadResp(i).valid

        io.rd_rat_write_req(i).areg := rename2_decodeRes(i).rd
        io.rd_rat_write_req(i).preg := io.freeRegBufferReadResp(i).preg

        io.freeRegBufferReadReq(i).valid := rename2_decodeRes(i).HasRd
        io.freeRegBufferReadReq(i).is_checkPoint := 
            rename2_decodeRes(i).opcode === OpcodeVal.BEQ ||
            rename2_decodeRes(i).opcode === OpcodeVal.LB ||
            rename2_decodeRes(i).opcode === OpcodeVal.SB ||
            rename2_decodeRes(i).opcode === OpcodeVal.JALR
    }


    // generate rename2 result
    for(i <- 0 until p(FETCH_WIDTH)) {
        io.renameRes(i).valid := rename2_decodeRes(i).valid
        io.renameRes(i).pc := rename2_decodeRes(i).pc
        io.renameRes(i).imm := rename2_decodeRes(i).imm
        io.renameRes(i).opcode := rename2_decodeRes(i).opcode
        io.renameRes(i).funct3 := rename2_decodeRes(i).funct3
        io.renameRes(i).funct7 := rename2_decodeRes(i).funct7
        io.renameRes(i).lat := rename2_decodeRes(i).lat
        io.renameRes(i).HasRs1 := rename2_decodeRes(i).HasRs1
        io.renameRes(i).HasRs2 := rename2_decodeRes(i).HasRs2
        io.renameRes(i).HasRd := rename2_decodeRes(i).HasRd
        io.renameRes(i).rs1 := rename2_decodeRes(i).rs1
        io.renameRes(i).rs2 := rename2_decodeRes(i).rs2
        io.renameRes(i).rd := rename2_decodeRes(i).rd
        io.renameRes(i).pd := io.freeRegBufferReadResp(i).preg
        io.renameRes(i).old_pd := Mux(
            rename2_has_waw_before(i), 
            io.freeRegBufferReadResp(rename2_waw_before_idx(i)).preg, 
            io.rd_rat_read_resp(i).preg
        )
        io.renameRes(i).ps1 := Mux(
            rename2_rs1_has_raw(i), 
            io.freeRegBufferReadResp(rename2_rs1_raw_idx(i)).preg, 
            io.rs1_rat_read_resp(i).preg
        )
        io.renameRes(i).ps2 := Mux(
            rename2_rs2_has_raw(i), 
            io.freeRegBufferReadResp(rename2_rs2_raw_idx(i)).preg, 
            io.rs2_rat_read_resp(i).preg
        )
        io.renameRes(i).branchPredInfo := rename2_br_pred_info_vec(i)
    }

    // hasRd should be equal to freeregIdResp Valid <=> freeRegIdBuffer can not provide enough preg for rename
    io.rename2_stall := 
        VecInit(rename2_decodeRes.map(_.HasRd)).asUInt === 
            VecInit(io.freeRegBufferReadResp.map(_.valid)).asUInt

}