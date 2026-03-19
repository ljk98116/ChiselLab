package MyCPU.frontend

import chisel3._
import chisel3.util._

import MyCPU.config._
import MyCPU.io._
import MyCPU.frontend.BPU._
import MyCPU.frontend.BPU.predictors._
import org.chipsalliance.cde.config._
import MyCPU.utils.PipeReg

class FrontEnd(implicit p: Parameters) extends Module {
    // if0 stage: generate fetch pc, access L0 & L1 BTB
    // if0 stage: access ITLB
    // if1 stage: get PHT, ICache, BTB Data
    // if2 stage: judge hit
    // if3 stage: gen redirect
    // if4 stage: write insts & valid bits & branch pred result
    // if5 stage: send insts to middle end

    // use l0 btb res & l1 btb res to generate next pred target
    // use final pred target to access RAS, piplined l0 pred result
    // l0 jump but l1 not jump, not save icache request, redirect to l1 pred target
    val io = IO(new Bundle {
        // from Top
        val icache_stall_req = Input(Bool())
        val stall_from_backpipe = Input(Bool())
        // from wb1 stage
        val commit_redirect = Input(new CommitUpdateInfo)
        // from wb1 stage
        val wb1_l1_btb_update = Input(Vec(p(FETCH_WIDTH), new L1BTBUpdateInfo))
        val wb1_predictor_update = Input(Vec(p(FETCH_WIDTH), new PredictorUpdateInfo))
        // to commit stage
        val wb1_l1_btb_enqReady = Output(Bool())
        val wb1_predictor_enqReady = Output(Bool())
        // from middle end
        val decode_l1_btb_update = Input(Vec(p(FETCH_WIDTH), new L1BTBUpdateInfo))
        // to iTLB
        val if0_itlb_req = Output(Vec(p(FETCH_WIDTH), new ITLBReqInfo))
        // from iTLB
        val if1_itlb_resp = Input(Vec(p(FETCH_WIDTH), new ITLBRespInfo))
        // to icache, use part of page offset as index
        val if0_icache_req = Output(new ICacheReadReq)
        // from icache
        val if2_icacheline = Input(Vec(p(L1CacheLineLength), UInt(p(DATA_WIDTH).W)))
        val if2_icache_hit = Input(Bool())
        val icache_refill_valid = Input(Bool())
        val icache_refill_pa = Input(new PhysicalAddress)
        val icache_refill_data = Input(Vec(p(L1CacheLineLength), UInt(p(DATA_WIDTH).W)))
        // to middle end
        val if5_pc_vec = Output(Vec(p(FETCH_WIDTH), UInt(p(ADDR_WIDTH).W)))
        val if5_inst = Output(Vec(p(FETCH_WIDTH), UInt(p(DATA_WIDTH).W)))
        val if5_instValid = Output(Vec(p(FETCH_WIDTH), Bool()))
        val if5_br_pred_info_vec = Output(Vec(p(FETCH_WIDTH), new BranchPredInfo))
        val canEnq = Output(Bool())
    })

    val l0_btb = Module(new L0BTB)
    val l1_btb = Module(new L1BTB)
    val predictor = Module(new GSharePredictor)
    val ras = Module(new RAS)

    val l1_btb_wb_queue = Module(new L1BTBWbQueue(32))
    val pred_wb_queue = Module(new PredictorWbQueue(32))
    val inst_buffer = Module(new InstBuffer(8))

    val offsetBits = 2
    val fetchBits = log2Ceil(p(FETCH_WIDTH))
    val rasPtrWidth = log2Ceil(p(RASParam).RASSize + 1)
    val counterBits = 2

    // prio: commit > decode > if3_redirect > if0_redirect
    val if3_redirect = WireInit(0.U.asTypeOf(new IF3RedirectInfo))
    val if0_redirect = WireInit(0.U.asTypeOf(new L0BTBRespInfo))

    val flush = io.commit_redirect.flush
    // wb1 stage
    val wb1_l1_btb_update = WireInit(VecInit(
        Seq.fill(p(FETCH_WIDTH))(0.U.asTypeOf(new L1BTBUpdateInfo))
    ))
    val wb1_pred_update = WireInit(VecInit(
        Seq.fill(p(FETCH_WIDTH))(0.U.asTypeOf(new PredictorUpdateInfo))
    ))

    l1_btb_wb_queue.io.enq := io.wb1_l1_btb_update
    pred_wb_queue.io.enq := io.wb1_predictor_update

    io.wb1_l1_btb_enqReady := l1_btb_wb_queue.io.enqReady
    io.wb1_predictor_enqReady := pred_wb_queue.io.enqReady

    l1_btb.io.update := l1_btb_wb_queue.io.deq
    predictor.io.update := pred_wb_queue.io.deq

    // if0 stage
    val pc = RegInit(0.U(p(ADDR_WIDTH).W))
    val nextPC = WireInit(0.U(p(ADDR_WIDTH).W))
    val redirectPC = WireInit(0.U(p(ADDR_WIDTH).W))
    val nextIsCall = WireInit(false.B)
    val nextIsReturn = WireInit(false.B)
    val nextRASTop = WireInit(0.U(rasPtrWidth.W))
    val rasUpdateData = WireInit(0.U(p(ADDR_WIDTH).W))

    val if0_ReqVec = WireInit(VecInit(
        Seq.fill(p(FETCH_WIDTH))(0.U(p(ADDR_WIDTH).W))
    ))

    val if0_ReqValid = WireInit(VecInit(
        Seq.fill(p(FETCH_WIDTH))(false.B)
    ))

    val if0_stall = io.icache_stall_req | predictor.io.if0_stall | !inst_buffer.io.canEnq | io.stall_from_backpipe
    val if1_stall = io.icache_stall_req | !inst_buffer.io.canEnq | io.stall_from_backpipe
    val if2_stall = io.icache_stall_req | !inst_buffer.io.canEnq | io.stall_from_backpipe
    val if3_stall = !inst_buffer.io.canEnq | io.stall_from_backpipe
    val if4_stall = !inst_buffer.io.canEnq | io.stall_from_backpipe

    l1_btb.io.if2_stall := if2_stall
    l1_btb.io.flush := flush
    
    for(i <- 0 until p(FETCH_WIDTH)) {
        val pc_loc = Cat(pc(p(ADDR_WIDTH) - 1, offsetBits + fetchBits), i.U(log2Ceil(p(FETCH_WIDTH)).W), 0.U(p(FETCH_OFFSET).W))
        if0_ReqVec(i) := pc_loc
        if0_ReqValid(i) := (i.U <= pc(offsetBits + fetchBits - 1, offsetBits)) && !if0_stall

        l0_btb.io.req(i).pc := if0_ReqVec(i)
        l0_btb.io.req(i).valid := if0_ReqValid(i)

        l1_btb.io.req(i).pc := if0_ReqVec(i)
        l1_btb.io.req(i).valid := if0_ReqValid(i)

        predictor.io.if0_req(i).pc := if0_ReqVec(i)
        predictor.io.if0_req(i).valid := if0_ReqValid(i)
    }

    l0_btb.io.rasTopPtr := ras.io.rasTopPtr

    when(io.commit_redirect.flush) {
        nextIsCall := io.commit_redirect.isCall
        nextIsReturn := io.commit_redirect.isReturn
        nextRASTop := io.commit_redirect.rasTopPtr
        rasUpdateData := Mux(io.commit_redirect.isCall, io.commit_redirect.flush_pc, 0.U)
    }
    // to do: decode redirect
    .elsewhen(if3_redirect.redirect) {
        nextIsCall := if3_redirect.isCall
        nextIsReturn := if3_redirect.isReturn
        nextRASTop := if3_redirect.rasTopPtr
        rasUpdateData := Mux(if3_redirect.isCall, if3_redirect.target, 0.U)
    }
    .elsewhen(if0_redirect.valid) {
        nextIsCall := if0_redirect.isCall
        nextIsReturn := if0_redirect.isReturn
        nextRASTop := if0_redirect.rasTopPtr
        rasUpdateData := Mux(if0_redirect.isCall, if0_redirect.target, 0.U)        
    }

    when(io.commit_redirect.flush) {
        redirectPC := io.commit_redirect.flush_pc
    }.elsewhen(if3_redirect.redirect) {
        redirectPC := if3_redirect.target
    }.elsewhen(if0_redirect.valid) {
        redirectPC := if0_redirect.target
    }

    nextPC := Mux(!if0_stall, pc + (p(FETCH_WIDTH) << p(FETCH_OFFSET)).U, pc)

    if0_redirect := l0_btb.io.resp
    
    ras.io.next_ras_top := Mux(
        io.commit_redirect.flush | if3_redirect.redirect | if0_redirect.valid, 
        redirectPC,
        nextPC
    )

    pc := Mux(
        io.commit_redirect.flush | if3_redirect.redirect | if0_redirect.valid, 
        redirectPC,
        nextPC
    )

    ras.io.next_ras_top_ptr := nextRASTop
    ras.io.next_ras_top_valid := nextIsCall && !if0_stall

    // itlb req
    for(i <- 0 until p(FETCH_WIDTH)) {
        io.if0_itlb_req(i).addr := if0_ReqVec(i)
        io.if0_itlb_req(i).valid := if0_ReqValid(i)
    }

    // icache req merge
    io.if0_icache_req.valid := if0_ReqValid.asUInt.orR
    io.if0_icache_req.index := if0_ReqVec(0)(
        p(ICacheIndexBits) + p(L1CacheOffsetBits) + p(FETCH_OFFSET) - 1, 
        p(FETCH_OFFSET) + p(L1CacheOffsetBits)
    )

    // if1 stage
    val if1_rasTopPtr = RegInit(0.U(rasPtrWidth.W))
    val if1_rasTop = RegInit(0.U(p(ADDR_WIDTH).W))

    if1_rasTop := Mux(flush, 0.U, Mux(!if0_stall, ras.io.rasTop, if1_rasTop))
    if1_rasTopPtr := Mux(flush, 0.U, Mux(!if0_stall, ras.io.rasTopPtr, if1_rasTopPtr))

    val if1_ReqVec = PipeReg(if0_ReqVec, if1_stall, flush)
    val if1_ReqValid = PipeReg(if0_ReqValid, if1_stall, flush)
    val if1_pred_resp = predictor.io.if1_predict
    val if1_l0_btb_resp = l0_btb.io.resp
    val if1_pa = io.if1_itlb_resp(0).paddr

    // if2 stage btb judge hit
    val if2_ReqVec = PipeReg(if1_ReqVec, if2_stall, flush)
    val if2_ReqValid = PipeReg(if1_ReqValid, if2_stall, flush)
    val if2_l0_btb_resp = PipeReg(if1_l0_btb_resp, if2_stall, flush)

    val if2_l1_btb_resp = l1_btb.io.resp
    val if2_rasTop = PipeReg(if1_rasTop, if2_stall, flush)
    val if2_rasTopPtr = PipeReg(if1_rasTopPtr, if2_stall, flush)
    val if2_pa = PipeReg(if1_pa, if2_stall, flush)

    // if1 do pht read, if3 judge redirect
    val if2_pred_resp = PipeReg(if1_pred_resp, if2_stall, flush)

    val if2_insts = io.if2_icacheline
    val if2_icache_hit = io.if2_icache_hit

    // if3 stage
    // judge redirect
    val if3_ReqVec = PipeReg(if2_ReqVec, if3_stall, flush)
    val if3_ReqValid = PipeReg(if2_ReqValid, if3_stall, flush)

    val if3_rasTop = PipeReg(if2_rasTop, if3_stall, flush)
    val if3_rasTopPtr = PipeReg(if2_rasTopPtr, if3_stall, flush)

    val if3_l0_btb_update = WireInit(0.U.asTypeOf(new L0BTBUpdateInfo))
    val if3_l1_btb_resp = RegNext(if2_l1_btb_resp)

    val if3_insts = PipeReg(if2_insts, if3_stall, flush)
    val if3_icache_hit = PipeReg(if2_icache_hit, if3_stall, flush)
    val if3_l0_btb_resp = PipeReg(if2_l0_btb_resp, if3_stall, flush)
    val if3_pred_resp = PipeReg(if2_pred_resp, if3_stall, flush)

    val if3_pa = PipeReg(if2_pa, if3_stall, flush)

    // get if3 l0 btb update infos
    // l1 btb hit different to l0 btb, l0 btb hit but different to l1, l0 btb not hit
    if3_l0_btb_update.valid := if3_l1_btb_resp.valid && (
        (if3_l1_btb_resp.target =/= if3_l0_btb_resp.target && if3_l0_btb_resp.valid) ||
        (~if3_l0_btb_resp.valid)
    )
    if3_l0_btb_update.pc := if3_ReqVec(if3_l1_btb_resp.hitIndex)
    if3_l0_btb_update.isCall := if3_l1_btb_resp.isCall
    if3_l0_btb_update.isReturn := if3_l1_btb_resp.isReturn
    if3_l0_btb_update.target := if3_l1_btb_resp.target

    val predResult = if3_pred_resp(if3_l1_btb_resp.hitIndex).counter(counterBits - 1)
    val predValid = if3_pred_resp(if3_l1_btb_resp.hitIndex).valid

    if3_redirect.redirect := (if3_l0_btb_update.valid ^ (if3_l1_btb_resp.valid && predResult && predValid)) ||
        (if3_l0_btb_update.target =/= if3_l1_btb_resp.target) ||
        (if3_l0_btb_update.isCall ^ if3_l1_btb_resp.isCall) ||
        (if3_l0_btb_update.isReturn ^ if3_l0_btb_update.isReturn)

    if3_redirect.target := Mux(
        if3_l1_btb_resp.valid && predResult && predValid, 
        if3_l1_btb_resp.target,
        if3_ReqVec(p(FETCH_WIDTH) - 1) + (p(FETCH_WIDTH) << offsetBits).U
    )
    if3_redirect.isCall := Mux(
        if3_l1_btb_resp.valid && predResult && predValid,
        if3_l1_btb_resp.isCall,
        false.B
    )
    if3_redirect.isReturn := Mux(
        if3_l1_btb_resp.valid && predResult && predValid,
        if3_l1_btb_resp.isReturn,
        false.B
    )
    if3_redirect.rasTopPtr := Mux(
        if3_l1_btb_resp.valid && predResult && predValid,
        if3_rasTopPtr + if3_l1_btb_resp.isCall - if3_l1_btb_resp.isReturn,
        if3_rasTopPtr        
    )
    l0_btb.io.update := if3_l0_btb_update

    // if4 stage
    val if4_branch_results = WireInit(VecInit(
        Seq.fill(p(FETCH_WIDTH))(0.U.asTypeOf(new BranchPredInfo))
    ))

    val if4_if3_redirect = PipeReg(if3_redirect, if4_stall, flush)
    val if4_l0_btb_resp = PipeReg(if3_l0_btb_resp, if4_stall, flush)
    val if4_ReqVec = PipeReg(if3_ReqVec, if4_stall, flush)
    val if4_ReqValid = PipeReg(if3_ReqValid, if4_stall, flush)
    val if4_pred_resp = PipeReg(if3_pred_resp, if4_stall, flush)
    val if4_insts = PipeReg(if3_insts, if4_stall, flush)
    val if4_icache_hit = PipeReg(if3_icache_hit, if4_stall, flush)

    val if4_noRedirectIsCall = Mux(if4_l0_btb_resp.valid, if4_l0_btb_resp.isCall, false.B)
    val if4_noRedirectIsReturn = Mux(if4_l0_btb_resp.valid, if4_l0_btb_resp.isReturn, false.B)
    val if4_pa = PipeReg(if3_pa, if4_stall, flush)

    for(i <- 0 until p(FETCH_WIDTH)) {
        if4_branch_results(i).valid := if4_ReqValid(i)
        if4_branch_results(i).counter := if4_pred_resp(i).counter
        if4_branch_results(i).ghist := if4_pred_resp(i).ghist
        if4_branch_results(i).lhist := if4_pred_resp(i).lhist
        if4_branch_results(i).target := Mux(
            if4_if3_redirect.redirect, 
            if4_if3_redirect.target, 
            Mux(if4_l0_btb_resp.valid, if4_l0_btb_resp.target, if4_ReqVec(i) + 4.U)
        )
        if4_branch_results(i).isCall := Mux(if4_if3_redirect.redirect, if4_if3_redirect.isCall, if4_noRedirectIsCall)
        if4_branch_results(i).isReturn := Mux(if4_if3_redirect.redirect, if4_if3_redirect.isReturn, if4_noRedirectIsReturn)
    }

    // if4 stage get final branch result and write into instBuffer
    io.canEnq := inst_buffer.io.canEnq
    inst_buffer.io.flush := io.commit_redirect.flush
    inst_buffer.io.stall := io.stall_from_backpipe

    inst_buffer.io.if4_pc_i := if4_ReqVec
    inst_buffer.io.if4_instValid_i := if4_ReqValid
    inst_buffer.io.if4_inst_i := if4_insts
    inst_buffer.io.if4_branch_result_i := if4_branch_results
    inst_buffer.io.if4_pa_i := if4_pa
    inst_buffer.io.if4_icache_hit_i := if4_icache_hit

    inst_buffer.io.refill_valid_i := io.icache_refill_valid
    inst_buffer.io.refill_pa_i := io.icache_refill_pa
    inst_buffer.io.refill_insts_i := io.icache_refill_data

    // if4 stage deq, if5 get deq item
    io.if5_inst := inst_buffer.io.if5_inst_o
    io.if5_pc_vec := inst_buffer.io.if5_pc_o
    io.if5_instValid := inst_buffer.io.if5_instValid_o
    io.if5_br_pred_info_vec := inst_buffer.io.if5_branch_result_o
}
