package MyCPU.frontend

import chisel3._
import chisel3.util._

import MyCPU.config._
import MyCPU.io._
import MyCPU.frontend.BPU._
import MyCPU.frontend.BPU.predictors._
import org.chipsalliance.cde.config._

class FrontEnd(implicit p: Parameters) extends Module {
    // if0 stage: generate fetch pc, access L0 & L1 BTB
    // if0 stage: access ITLB
    // if1 stage: get PHT, ICache, BTB Data
    // if2 stage: judge hit, gen redirect
    // if3 stage: write insts & valid bits & branch pred result to InstBuffer

    // use l0 btb res & l1 btb res to generate next pred target
    // use final pred target to access RAS, piplined l0 pred result
    // l0 jump but l1 not jump, not save icache request, redirect to l1 pred target
    val io = IO(new Bundle {
        // from Top
        val icache_stall_req = Input(Bool())
        val predictor_stall_req = Input(Bool())
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
        val if1_icacheline = Input(Vec(p(L1CacheLineLength), UInt(p(DATA_WIDTH).W)))
        val if2_icache_hit = Input(Bool())
        // to middle end
        val if4_inst = Output(Vec(p(FETCH_WIDTH), UInt(p(DATA_WIDTH).W)))
        val if4_instValid = Output(Vec(p(FETCH_WIDTH), Bool()))
        val if4_instHit = Output(Bool())
        val if4_br_pred_info_vec = Output(Vec(p(FETCH_WIDTH), new BranchPredInfo))
    })

    val l0_btb = Module(new L0BTB)
    val l1_btb = Module(new L1BTB)
    val predictor = Module(new GSharePredictor)
    val ras = Module(new RAS)

    val l1_btb_wb_queue = Module(new L1BTBWbQueue(32))
    val pred_wb_queue = Module(new PredictorWbQueue(32))
    // val inst_buffer = Module(new InstBuffer(64))

    val offsetBits = 2
    val fetchBits = log2Ceil(p(FETCH_WIDTH))
    val rasPtrWidth = log2Ceil(p(RASParam).RASSize + 1)
    val counterBits = 2

    // prio: commit > decode > if3_redirect > if0_redirect
    val if3_redirect = WireInit(0.U.asTypeOf(new IF3RedirectInfo))
    val if0_redirect = WireInit(0.U.asTypeOf(new L0BTBRespInfo))

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

    val if0_stall = io.icache_stall_req | io.predictor_stall_req

    for(i <- 0 until p(FETCH_WIDTH)) {
        val pc_loc = pc(p(ADDR_WIDTH) - 1, offsetBits + fetchBits) | (i << offsetBits).U
        if0_ReqVec(i) := pc_loc
        if0_ReqValid(i) := ~(pc(offsetBits + fetchBits, offsetBits) > i.U) & !if0_stall

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

    if1_rasTop := ras.io.rasTop
    if1_rasTopPtr := ras.io.rasTopPtr

    val if1_ReqVec = RegNext(if0_ReqVec)
    val if1_ReqValid = RegNext(if0_ReqValid)
    val if1_pred_resp = predictor.io.if1_predict
    val if1_l0_btb_resp = l0_btb.io.resp

    // if2 stage btb judge hit
    val if2_ReqVec = RegNext(if1_ReqVec)
    val if2_ReqValid = RegNext(if1_ReqValid)
    val if2_l0_btb_resp = RegNext(if1_l0_btb_resp)

    val if2_l1_btb_resp = l1_btb.io.resp
    val if2_rasTop = RegNext(if1_rasTop)
    val if2_rasTopPtr = RegNext(if1_rasTopPtr)

    // if1 do pht read, if3 judge redirect
    val if2_pred_resp = RegNext(if1_pred_resp)

    val if2_insts = RegNext(io.if1_icacheline)
    val if2_icache_hit = io.if2_icache_hit

    // if3 stage
    // judge redirect
    val if3_ReqVec = RegNext(if2_ReqVec)
    val if3_ReqValid = RegNext(if2_ReqValid)

    val if3_rasTop = RegNext(if2_rasTop)
    val if3_rasTopPtr = RegNext(if2_rasTopPtr)

    val if3_l0_btb_update = WireInit(0.U.asTypeOf(new L0BTBUpdateInfo))
    val if3_l1_btb_resp = RegNext(if2_l1_btb_resp)

    val if3_insts = RegNext(if2_insts)
    val if3_icache_hit = RegNext(if2_icache_hit)
    val if3_l0_btb_resp = RegNext(if2_l0_btb_resp)
    val if3_pred_resp = RegNext(if2_pred_resp)

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
        if3_rasTop + if3_l1_btb_resp.isCall - if3_l1_btb_resp.isReturn,
        if3_rasTop        
    )
    l0_btb.io.update := if3_l0_btb_update

    // if4 stage
    val if4_branch_results = WireInit(VecInit(
        Seq.fill(p(FETCH_WIDTH))(0.U.asTypeOf(new BranchPredInfo))
    ))

    val if4_if3_redirect = RegNext(if3_redirect)
    val if4_l0_btb_resp = RegNext(if3_l0_btb_resp)
    val if4_ReqVec = RegNext(if3_ReqVec)
    val if4_ReqValid = RegNext(if3_ReqValid)
    val if4_pred_resp = RegNext(if3_pred_resp)
    val if4_insts = RegNext(if3_insts)
    val if4_icache_hit = RegNext(if3_icache_hit)

    val if4_noRedirectTarget = Mux(if4_l0_btb_resp.valid, if4_l0_btb_resp.target, 0.U)
    val if4_noRedirectIsCall = Mux(if4_l0_btb_resp.valid, if4_l0_btb_resp.isCall, false.B)
    val if4_noRedirectIsReturn = Mux(if4_l0_btb_resp.valid, if4_l0_btb_resp.isReturn, false.B)

    for(i <- 0 until p(FETCH_WIDTH)) {
        if4_branch_results(i).valid := if3_ReqValid(i)
        if4_branch_results(i).counter := if3_pred_resp(i).counter
        if4_branch_results(i).ghist := if3_pred_resp(i).ghist
        if4_branch_results(i).lhist := if3_pred_resp(i).lhist
        if4_branch_results(i).target := Mux(if4_if3_redirect.redirect, if4_if3_redirect.target, if4_noRedirectTarget)
        if4_branch_results(i).isCall := Mux(if4_if3_redirect.redirect, if4_if3_redirect.isCall, if4_noRedirectIsCall)
        if4_branch_results(i).isReturn := Mux(if4_if3_redirect.redirect, if4_if3_redirect.isReturn, if4_noRedirectIsReturn)
    }

    io.if4_inst := if4_insts
    io.if4_instValid := if4_ReqValid
    io.if4_instHit := if4_icache_hit

    for(i <- 0 until p(FETCH_WIDTH)) {
        io.if4_br_pred_info_vec(i) := 0.U.asTypeOf(new BranchPredInfo)
    }

    // if4 stage get final branch result and write into instBuffer
}
