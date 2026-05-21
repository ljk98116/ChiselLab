package MyCPU.middleend

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._

import org.chipsalliance.cde.config._
import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

// 5 stage
// decode -> rename1 -> rename2 -> dispatch1 -> dispatch2
class MiddleEnd(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        //from if5
        val if5_pc_vec_i = Input(Vec(p(FETCH_WIDTH), UInt(p(ADDR_WIDTH).W)))
        val if5_inst_i = Input(Vec(p(FETCH_WIDTH), UInt(p(DATA_WIDTH).W)))
        val if5_instValid_i = Input(Vec(p(FETCH_WIDTH), Bool()))
        val if5_br_pred_info_vec_i = Input(Vec(p(FETCH_WIDTH), new BranchPredInfo))
        // commit stage io
        val commit_redirect = Input(new CommitUpdateInfo)
        val stall_from_backpipe = Input(Bool())
        val commit_refill_write_back = Flipped(DecoupledIO(Vec(p(FETCH_WIDTH), new RATSpecWriteReq)))
        val commit_spec_write_back = Input(Vec(p(FETCH_WIDTH), new FreeRegIdBufferWriteReq))
        val commit_spec_robId_write_back = Input(Vec(p(FETCH_WIDTH), new ROBFreeIdWriteReq))
        val loadRAWHarvard = Flipped(DecoupledIO(new LoadRAWHarvardInfo))
        // to prf or others
        val checkPointInfo = Output(new CheckPointInfo)
        // prf io
        val busyTables = Input(Vec(p(FETCH_WIDTH), UInt((1 << p(PREG_WIDTH)).W)))
        // to front end
        val rename1_stall = Output(Bool())
        val rename2_stall = Output(Bool())
        // to issue enq stage
        val load_uops = Output(Vec(p(FETCH_WIDTH), new uOp))
        val store_uops = Output(Vec(p(FETCH_WIDTH), new uOp))
        val alu_uops = Output(Vec(p(FETCH_WIDTH), new uOp))
        val sys_uops = Output(Vec(p(FETCH_WIDTH), new uOp)) 
        // to rob[dispatch1]
        val inorder_uops = Output(Vec(p(FETCH_WIDTH), new uOp))
        val inorder_locs = Input(Vec(p(FETCH_WIDTH), UInt(p(ROBID_WIDTH).W)))
    })

    val decode = Module(new DecodeStage)
    val rename = Module(new RenameStages)
    val dispatch = Module(new DispatchStage)
    val freeRegIdBuffer = Module(new FreeRegIdBuffer)
    val checkPointQueue = Module(new CheckPointQueue(64))
    val ratSpec = Module(new RATSpec)
    val mdp = Module(new StoreSet)
    val robFreeIdBuffer = Module(new ROBFreeIdBuffer)

    val flush = io.commit_redirect.flush

    decode.io.flush := flush
    decode.io.stall := io.stall_from_backpipe || io.rename2_stall || io.rename1_stall
    decode.io.if5_pc_vec_i := io.if5_pc_vec_i
    decode.io.if5_inst_i := io.if5_inst_i
    decode.io.if5_instValid_i := io.if5_instValid_i
    decode.io.if5_br_pred_info_vec_i := io.if5_br_pred_info_vec_i
    
    rename.io.decodeRes := decode.io.decodeRes_o
    rename.io.br_pred_info_vec := decode.io.br_pred_info_vec
    rename.io.flush := flush
    rename.io.stall_from_backpipe := io.stall_from_backpipe
    rename.io.deqReady := freeRegIdBuffer.io.deqReady

    io.rename2_stall := rename.io.rename2_stall

    freeRegIdBuffer.io.readReq := rename.io.freeRegBufferReadReq
    rename.io.freeRegBufferReadResp := freeRegIdBuffer.io.readResp

    ratSpec.io.rs1_rat_read_req := rename.io.rs1_rat_read_req
    rename.io.rs1_rat_read_resp := ratSpec.io.rs1_rat_read_resp

    ratSpec.io.rs2_rat_read_req := rename.io.rs2_rat_read_req
    rename.io.rs2_rat_read_resp := ratSpec.io.rs2_rat_read_resp

    ratSpec.io.rd_rat_read_req := rename.io.rd_rat_read_req
    rename.io.rd_rat_read_resp := ratSpec.io.rd_rat_read_resp    

    ratSpec.io.rd_rat_write_req := rename.io.rd_rat_write_req

    ratSpec.io.flush := flush
    ratSpec.io.commit_refill_write_back <> io.commit_refill_write_back

    dispatch.io.renameRes_i := rename.io.renameRes

    dispatch.io.flush := flush
    dispatch.io.stall_from_backpipe := io.stall_from_backpipe
    dispatch.io.dispatch1_canSpecExec := mdp.io.dispatch1_canSpecExec
    dispatch.io.dispatch1_blockStIds := mdp.io.dispatch1_blockStIds

    io.load_uops := dispatch.io.load_uops
    io.store_uops := dispatch.io.store_uops
    io.alu_uops := dispatch.io.alu_uops
    io.sys_uops := dispatch.io.sys_uops
    io.inorder_uops := dispatch.io.inorder_uops
    
    dispatch.io.inorder_locs := io.inorder_locs

    checkPointQueue.io.flush := flush
    checkPointQueue.io.enq := freeRegIdBuffer.io.currentHeads

    freeRegIdBuffer.io.checkpointInfo := checkPointQueue.io.deq
    freeRegIdBuffer.io.writeReq := io.commit_spec_write_back
    freeRegIdBuffer.io.busyTables := io.busyTables

    mdp.io.loadRAWHarvardInfo <> io.loadRAWHarvard
    mdp.io.flush := flush
    mdp.io.stall_from_backpipe := io.stall_from_backpipe
    mdp.io.rename2_stall := io.rename2_stall
    mdp.io.rename1_stall := io.rename1_stall
    mdp.io.decode_pcs := VecInit(decode.io.decodeRes_o.map(_.pc))
    mdp.io.rename1_isLoadVec := rename.io.rename1_is_load
    mdp.io.rename1_isStoreVec := rename.io.rename1_is_store
    mdp.io.rename1_pcs := rename.io.rename1_pcs
    mdp.io.rename1_robIds := rename.io.rename1_robIds
    mdp.io.decode_pcValids := VecInit(decode.io.decodeRes_o.map(_.valid))

    io.rename1_stall := !robFreeIdBuffer.io.canDeq
    robFreeIdBuffer.io.flush := flush
    robFreeIdBuffer.io.commitWriteBack := io.commit_spec_robId_write_back
    robFreeIdBuffer.io.rename1ReadReq := rename.io.robFreeIdAllocReq
    rename.io.robFreeIdAllocResp := robFreeIdBuffer.io.rename1ReadResp

    io.checkPointInfo := checkPointQueue.io.deq
}