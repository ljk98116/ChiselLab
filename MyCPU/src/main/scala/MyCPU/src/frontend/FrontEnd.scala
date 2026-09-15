package MyCPU.frontend

import chisel3._
import chisel3.util._

import MyCPU.config._
import MyCPU.io._
import MyCPU.frontend.BPU._
import MyCPU.frontend.BPU.predictors._
import org.chipsalliance.cde.config._
import MyCPU.utils._


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
        val wb1_predictor_enqReady = Output(Bool())
        // to iTLB
        val if0_itlb_req = Output(Vec(p(FETCH_WIDTH), new ITLBReqInfo))
        // from iTLB
        val if1_itlb_resp = Input(Vec(p(FETCH_WIDTH), new ITLBRespInfo))
        // to icache, use part of page offset as index
        val if0_icache_req = Output(new ICacheReadReq)
        // from icache
        val if2_insts = Input(Vec(p(FETCH_WIDTH), UInt(p(DATA_WIDTH).W)))
        val if2_icache_hit = Input(Bool())
        val if2_predecode = Input(Vec(p(FETCH_WIDTH), new ICacheBranchMetaInfo))
        val if2_mshr_id = Input(UInt(log2Ceil(p(ICacheMSHRNum)).W))
        val if2_mshr_id_valid = Input(Bool())
        // to pipeline if5 stage
        val if5_mshr_id = Input(UInt(log2Ceil(p(ICacheMSHRNum)).W))
        val if5_mshr_id_valid = Input(Bool())
        val if5_icache_refill_resp = Input(new ICacheRefillResp)
        // to middle end
        val if5_pc_vec = Output(Vec(p(FETCH_WIDTH), UInt(p(ADDR_WIDTH).W)))
        val if5_inst = Output(Vec(p(FETCH_WIDTH), UInt(p(DATA_WIDTH).W)))
        val if5_instValid = Output(Vec(p(FETCH_WIDTH), Bool()))
        val if5_br_pred_info_vec = Output(Vec(p(FETCH_WIDTH), new BranchPredInfo))
        val canEnq = Output(Bool())
        // debug
        val if2_pc_vec = Output(Vec(p(FETCH_WIDTH), UInt(p(ADDR_WIDTH).W)))
        val if2_valid_vec = Output(Vec(p(FETCH_WIDTH), Bool()))
        val btbaccessCount = Output(UInt(64.W))
        val l0btbhitCount = Output(UInt(64.W))
        val l1btbhitCount = Output(UInt(64.W))
        val refGHR = Input(UInt(p(GShareParamKey).BPUPredictorGlobalHistoryBits.W))
        // debug
        val pushCount = Output(UInt(32.W))
        val popCount = Output(UInt(32.W))
    })

    dontTouch(io.refGHR)

    // debug counter
    val btbaccessCount = RegInit(0.U(64.W))
    val l0btbhitCount = RegInit(0.U(64.W))
    val l1btbhitCount = RegInit(0.U(64.W))

    io.btbaccessCount := btbaccessCount
    io.l0btbhitCount := l0btbhitCount
    io.l1btbhitCount := l1btbhitCount

    val l0_btb = Module(new L0BTB)
    val l1_btb = Module(new L1BTB)
    val predictor = Module(new GSharePredictor)
    val ras = Module(new RAS)

    val pred_wb_queue = Module(new PredictorWbQueue(32))
    val inst_buffer = Module(new InstBuffer(p(FETCH_WIDTH)))

    val offsetBits = p(FETCH_OFFSET)
    val fetchBits = log2Ceil(p(FETCH_WIDTH))
    val rasPtrWidth = log2Ceil(p(RASParam).RASSize)
    val counterBits = 2

    // prio: commit > decode > if3_redirect > if1_redirect > if0_redirect
    val if3_redirect = WireInit(0.U.asTypeOf(new IF3RedirectInfo))
    val if0_redirect = WireInit(0.U.asTypeOf(new L0BTBRespInfo))

    val flush = io.commit_redirect.flush
    val if1_flush = flush || if3_redirect.redirect
    val if2_flush = flush || if3_redirect.redirect
    val if3_flush = flush

    // commit_redirect because of branch mispred ?
    // if0 stall 4 period
    val misPredPenaltyCounter = RegInit(0.U(3.W))
    misPredPenaltyCounter := Mux(
        io.commit_redirect.flush, 
        4.U, 
        Mux(misPredPenaltyCounter === 0.U, misPredPenaltyCounter, misPredPenaltyCounter - 1.U)
    )

    // wb1 stage
    val wb1_l1_btb_update = WireInit(VecInit(
        Seq.fill(p(FETCH_WIDTH))(0.U.asTypeOf(new L1BTBUpdateInfo))
    ))
    val wb1_pred_update = WireInit(VecInit(
        Seq.fill(p(FETCH_WIDTH))(0.U.asTypeOf(new PredictorUpdateInfo))
    ))

    pred_wb_queue.io.flush := flush
    pred_wb_queue.io.enq := io.wb1_predictor_update

    io.wb1_predictor_enqReady := pred_wb_queue.io.enqReady

    l1_btb.io.update := io.wb1_l1_btb_update
    predictor.io.update := pred_wb_queue.io.deq

    // if0 stage
    val pc = RegInit("h80000000".U(p(ADDR_WIDTH).W))
    val nextPC = WireInit(0.U(p(ADDR_WIDTH).W))
    val redirectPC = WireInit(0.U(p(ADDR_WIDTH).W))
    val nextIsCall = WireInit(false.B)
    val nextIsReturn = WireInit(false.B)
    val nextRASTopPtr = WireInit(0.U(rasPtrWidth.W))
    val nextRASRedirect = WireInit(false.B)
    val nextRASRedirectPC = WireInit(0.U(p(ADDR_WIDTH).W))

    val if0_ReqVec = WireInit(VecInit(
        Seq.fill(p(FETCH_WIDTH))(0.U(p(ADDR_WIDTH).W))
    ))

    val if0_ReqValid = WireInit(VecInit(
        Seq.fill(p(FETCH_WIDTH))(false.B)
    ))

    val if0_stall = 
        io.icache_stall_req | 
        !inst_buffer.io.canEnq | io.stall_from_backpipe |
        misPredPenaltyCounter =/= 0.U
    
    val if1_stall = io.icache_stall_req | !inst_buffer.io.canEnq | io.stall_from_backpipe
    val if2_stall = io.icache_stall_req | !inst_buffer.io.canEnq | io.stall_from_backpipe
    val if3_stall = !inst_buffer.io.canEnq | io.stall_from_backpipe
    val if4_stall = !inst_buffer.io.canEnq | io.stall_from_backpipe

    l1_btb.io.if1_stall := if1_stall
    l1_btb.io.if2_stall := if2_stall

    l1_btb.io.if1_flush := if1_flush
    l1_btb.io.if2_flush := if2_flush    
    // predictor use l0 btb hit to pred if1 GHRSpec
    predictor.io.commit_flush := io.commit_redirect.flush
    predictor.io.if2_flush := io.commit_redirect.flush | if3_redirect.redirect
    predictor.io.if1_flush := io.commit_redirect.flush | if3_redirect.redirect
    predictor.io.if0_stall := if0_stall
    predictor.io.if1_stall := if1_stall
    predictor.io.if2_stall := if2_stall
    predictor.io.if2_predecode := io.if2_predecode
    predictor.io.if3_redirect := if3_redirect

    btbaccessCount := btbaccessCount + PopCount(if0_ReqValid)

    val pcLocs = WireInit(VecInit(
        Seq.tabulate(p(FETCH_WIDTH))(
            i => Cat(
                pc(p(ADDR_WIDTH) - 1, offsetBits + fetchBits), 
                i.U(fetchBits.W), 
                0.U(offsetBits.W)
            )
        )
    ))

    // printf("current pc: 0x%x\n", pc)
    // 00 00  0,1,2,3
    // 01 00  x,1,2,3
    // 10 00  x,x,2,3
    // 11 00  x,x,x,3
    // 
    for(i <- 0 until p(FETCH_WIDTH)) {
        if0_ReqVec(i) := Mux(!if0_stall, pcLocs(i), 0.U)

        if0_ReqValid(i) := (pc(offsetBits + fetchBits - 1, offsetBits) <= i.U) && !if0_stall
        l0_btb.io.req(i).pc := if0_ReqVec(i)
        l0_btb.io.req(i).valid := if0_ReqValid(i)

        l1_btb.io.req(i).pc := if0_ReqVec(i)
        l1_btb.io.req(i).valid := if0_ReqValid(i)

        predictor.io.if0_req(i).pc := if0_ReqVec(i)
        predictor.io.if0_req(i).valid := if0_ReqValid(i)
    }

    when(io.commit_redirect.flush) {
        nextIsCall := io.commit_redirect.isCall
        nextIsReturn := io.commit_redirect.isReturn
        nextRASTopPtr := io.commit_redirect.rasTopPtr
        nextRASRedirect := true.B
        nextRASRedirectPC := io.commit_redirect.pc
    }

    when(io.commit_redirect.flush) {
        redirectPC := io.commit_redirect.flush_pc
    }.elsewhen(if3_redirect.redirect) {
        redirectPC := if3_redirect.target
    }.elsewhen(if0_redirect.valid) {
        redirectPC := if0_redirect.target
    }

    val idx = pc(fetchBits + offsetBits - 1, offsetBits)
    val idx_inc = p(FETCH_WIDTH).U((fetchBits + 1).W) - idx

    nextPC := Mux(!if0_stall, pc + Cat(idx_inc, 0.U(offsetBits.W)), pc)

    if0_redirect := l0_btb.io.resp

    pc := Mux(
        io.commit_redirect.flush | if3_redirect.redirect | if0_redirect.valid, 
        redirectPC,
        nextPC
    )

    ras.io.redirectValid := nextRASRedirect
    ras.io.redirectIsCall := nextIsCall
    ras.io.redirectIsReturn := nextIsReturn
    ras.io.redirectPC := nextRASRedirectPC
    ras.io.redirectRasTopPtr := nextRASTopPtr

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
    io.if0_icache_req.offset := if0_ReqVec(0)(p(FETCH_OFFSET) + p(L1CacheOffsetBits) - 1, p(FETCH_OFFSET))
    // if1 stage
    val if1_ReqVec = PipeReg(if0_ReqVec, if1_stall, if1_flush)
    val if1_ReqValid = PipeReg(if0_ReqValid, if1_stall, if1_flush)
    val if1_ReqValid_o = WireInit(VecInit(
        Seq.fill(p(FETCH_WIDTH))(false.B)
    ))

    for(i <- 0 until p(FETCH_WIDTH)) {
        when(if0_redirect.valid && if0_redirect.hitIndex < i.U) {
            if1_ReqValid_o(i) := false.B
        }.otherwise {
            if1_ReqValid_o(i) := if1_ReqValid(i)
        }
    }

    val if1_l0_btb_hit = PipeReg(l0_btb.io.l0_btb_hit, if1_stall, if1_flush)

    l1_btb.io.if1_reqPC := if1_ReqVec
    l1_btb.io.if1_reqValid := if1_ReqValid

    predictor.io.if1_l0_btb_hit := if1_l0_btb_hit

    val if1_pred_resp = predictor.io.if1_predict
    val if1_l0_btb_resp = PipeReg(l0_btb.io.resp, if1_stall, if1_flush)

    val if1_pa = if(p(UseTLB)) {
        io.if1_itlb_resp(0).paddr
    } else {
        val paWire = WireInit(0.U.asTypeOf(new PhysicalAddress))
        // When not using a TLB, derive a PhysicalAddress from the request PC
        paWire.bits := if1_ReqVec(0) - "h80000000".U
        paWire
    }

    // if2 stage btb judge hit, gshare judge mispred
    val if2_ReqVec = PipeReg(if1_ReqVec, if2_stall, if2_flush)
    val if2_ReqValid = PipeReg(if1_ReqValid_o, if2_stall, if2_flush)
    val if2_l0_btb_resp = PipeReg(if1_l0_btb_resp, if2_stall, if2_flush)

    val if2_l1_btb_resp = l1_btb.io.resp

    val if2_rasTop = WireInit(0.U(p(ADDR_WIDTH).W))
    if2_rasTop := ras.io.rasTop

    val if2_rasTopPtr = WireInit(0.U(rasPtrWidth.W))
    if2_rasTopPtr := ras.io.rasTopPtr

    val if2_pa = PipeReg(if1_pa, if2_stall, if2_flush)

    // if1 do pht read, if3 judge redirect
    val if2_pred_resp = PipeReg(if1_pred_resp, if2_stall, if2_flush)

    val if2_insts = io.if2_insts
    val if2_icache_hit = io.if2_icache_hit

    io.if2_pc_vec := if2_ReqVec
    io.if2_valid_vec := if2_ReqValid

    l1_btb.io.if2_reqPC := if2_ReqVec
    l1_btb.io.if2_reqValid := if2_ReqValid
    
    // process predecode res, get first call/return/branch idx
    val if2_predecode = WireInit(VecInit(
        Seq.fill(p(FETCH_WIDTH))(0.U.asTypeOf(new ICacheBranchMetaInfo))
    ))

    if2_predecode := Mux(io.if2_icache_hit, io.if2_predecode, 
        VecInit(
            Seq.fill(p(FETCH_WIDTH))(0.U.asTypeOf(new ICacheBranchMetaInfo))
        )
    )

    val if2_pd_call   = VecInit(if2_predecode.zip(if2_ReqValid).map { case (p, v) => p.isCall   && v })
    val if2_pd_return = VecInit(if2_predecode.zip(if2_ReqValid).map { case (p, v) => p.isReturn && v })
    val if2_pd_condJump = VecInit(if2_predecode.zip(if2_ReqValid).map { 
        case (p, v) => !p.isUnconditionalBranch && v 
    })
    
    val if2_hasCall   = if2_pd_call.asUInt.orR
    val if2_hasReturn = if2_pd_return.asUInt.orR
    val if2_hasCondJump = if2_pd_condJump.asUInt.orR

    val if2_callIdx   = PriorityEncoder(if2_pd_call)
    val if2_returnIdx = PriorityEncoder(if2_pd_return)
    val if2_firstCondJumpIdx = PriorityEncoder(if2_pd_condJump)

    val if2_minRASIdx = WireInit(0.U(log2Ceil(p(FETCH_WIDTH)).W))
    val if2_callOrRet = VecInit((0 until p(FETCH_WIDTH)).map(i => if2_pd_call(i) || if2_pd_return(i)))
    if2_minRASIdx := PriorityEncoder(if2_callOrRet)
    
    // if3 stage
    // judge redirect
    val if3_ReqVec = PipeReg(if2_ReqVec, if3_stall, if3_flush)
    val if3_ReqValid = PipeReg(if2_ReqValid, if3_stall, if3_flush)

    val if3_rasTop = PipeReg(if2_rasTop, if3_stall, if3_flush)
    val if3_rasTopPtr = PipeReg(if2_rasTopPtr, if3_stall, if3_flush)

    val if3_l0_btb_update = WireInit(0.U.asTypeOf(new L0BTBUpdateInfo))
    val if3_l1_btb_resp = PipeReg(if2_l1_btb_resp, if3_stall, if3_flush)

    val if3_insts = PipeReg(if2_insts, if3_stall, if3_flush)
    val if3_icache_hit = PipeReg(if2_icache_hit, if3_stall, if3_flush)
    val if3_l0_btb_resp = PipeReg(if2_l0_btb_resp, if3_stall, if3_flush)
    val if3_pred_resp = PipeReg(if2_pred_resp, if3_stall, if3_flush)
    val if3_mshr_id = PipeReg(io.if2_mshr_id, if3_stall, if3_flush)
    val if3_mshr_id_valid = PipeReg(io.if2_mshr_id_valid, if3_stall, if3_flush)

    val if3_pa = PipeReg(if2_pa, if3_stall, if3_flush)

    val if3_minRASIdx = PipeReg(if2_minRASIdx, if3_stall, if3_flush)
    val if3_callIdx   = PipeReg(if2_callIdx, if3_stall, if3_flush)
    val if3_returnIdx = PipeReg(if2_returnIdx, if3_stall, if3_flush)
    val if3_firstCondJumpIdx = PipeReg(if2_firstCondJumpIdx, if3_stall, if3_flush)

    val if3_hasCall   = PipeReg(if2_hasCall, if3_stall, if3_flush)
    val if3_hasReturn = PipeReg(if2_hasReturn, if3_stall, if3_flush)
    val if3_hasCondJump = PipeReg(if2_hasCondJump, if3_stall, if3_flush)

    val predResult = if3_pred_resp(if3_l1_btb_resp.hitIndex).counter(counterBits - 1)
    val firstValidIndex = WireInit(0.U(log2Ceil(p(FETCH_WIDTH)).W))

    dontTouch(firstValidIndex)
    firstValidIndex := PriorityEncoder(if3_ReqValid)

    val l1_hit = if3_l1_btb_resp.valid
    val l0_hit = if3_l0_btb_resp.valid

    val predValid = if3_pred_resp(if3_l1_btb_resp.hitIndex).valid
    val predTaken = if3_pred_resp(if3_l1_btb_resp.hitIndex).counter(counterBits - 1)
    
    // (1) L1 btb hit && predTaken is not uncond branch -> ras not update
    // (2) L0 btb hit && predTaken && is not uncond branch -> ras not update

    // call pred right/wrong, cpu always jump to the correct pc, 
    // so when prev not has branch or prev branch not valid, call will always push to RAS
    
    // but when prev branch pred not jump is wrong, ras hold the wrong item, so we need to recover RAS with the
    // old rasTopPtr [no inc or dec]

    val l1_taken = l1_hit && (predValid && predTaken)
    val l0_taken = l0_hit && (predValid && predTaken)

    val canRasUpdate = !(
        (l1_taken && if3_hasCondJump && if3_l1_btb_resp.hitIndex === if3_firstCondJumpIdx) ||
        (l0_taken && if3_hasCondJump && if3_l0_btb_resp.hitIndex === if3_firstCondJumpIdx)
    )
    
    ras.io.if3_isCall := canRasUpdate && if3_hasCall
    ras.io.if3_isReturn := canRasUpdate && if3_hasReturn
    ras.io.if3_pc := if3_ReqVec(if3_minRASIdx)
    ras.io.if3_pcValid := if3_ReqValid(if3_minRASIdx)

    // get if3 l0 btb update infos
    // l0 btb miss 
    // current l0 btb not use ras result to update 
    // l0 btb miss, l1 btb hit (pred jump or not jump) 
    // l0 btb hit but l1 btb miss

    // l0 btb hit l1 btb hit, but content different
    // if l1 btb hit and pred jump, use l1 btb hitIndex to mask behind req invalid
    // 
    if3_l0_btb_update.valid := 
        (l0_hit && !(predValid && predResult)) ||
        (!l0_hit && predValid) ||
        (
            if3_l0_btb_resp.valid && predValid && predResult &&
            ((if3_l0_btb_resp.isCall ^ if3_l1_btb_resp.isCall) ||
            (if3_l0_btb_resp.isReturn ^ if3_l1_btb_resp.isReturn) ||
            (if3_l0_btb_resp.target =/= if3_l1_btb_resp.target) ||
            (if3_l0_btb_resp.hitIndex =/= if3_l1_btb_resp.hitIndex))
        )

    val l0_hit_pc = WireInit(0.U(p(ADDR_WIDTH).W))
    l0_hit_pc := if3_ReqVec(if3_l0_btb_resp.hitIndex)
    dontTouch(l0_hit_pc)

    if3_l0_btb_update.pc := if3_ReqVec(if3_l1_btb_resp.hitIndex)
    dontTouch(if3_l0_btb_update.pc)

    if3_l0_btb_update.isCall := if3_l1_btb_resp.isCall
    if3_l0_btb_update.isReturn := if3_l1_btb_resp.isReturn
    if3_l0_btb_update.target := if3_l1_btb_resp.target

    // redirect means what ?
    // 
    // judge l1 btb hit && pred no branch
    // l0 btb hit but if3 pred not jump (l1 btb miss || pred not jump)
    // l0 btb miss l1 btb hit && jump
    // if3_isCall && if3_rasIsCall
    if3_redirect.redirect :=
        (ras.io.if3_isReturn) ||
        (if3_l0_btb_resp.valid && !l1_taken) ||
        (!if3_l0_btb_resp.valid && l1_taken) ||
        (
            if3_l0_btb_resp.valid && l1_taken && 
            ((if3_l0_btb_resp.isCall ^ if3_l1_btb_resp.isCall) ||
            (if3_l0_btb_resp.isReturn ^ if3_l1_btb_resp.isReturn) ||
            (if3_l0_btb_resp.target =/= if3_l1_btb_resp.target) ||
            (if3_l0_btb_resp.hitIndex =/= if3_l1_btb_resp.hitIndex))
        )

    // target
    val seqTarget = if3_ReqVec(firstValidIndex) + 4.U

    val l0_fallthrough = l0_hit_pc + 4.U

    val use_l0_pred = WireInit(false.B)

    use_l0_pred := if3_l0_btb_resp.valid && if3_ReqValid(if3_l0_btb_resp.hitIndex)

    when(l1_taken) {
        if3_redirect.target := if3_l1_btb_resp.target
    }
    .elsewhen(if3_l1_Call) {
        if3_redirect.target := if3_ReqVec(if3_firstCall) + 4.U
    }
    .otherwise {
        if3_redirect.target := Mux(use_l0_pred, l0_fallthrough, seqTarget)
    }

    // pc（redirect来源指令）
    if3_redirect.pc := Mux(
        (if3_rasIsReturn && if3_rasTopPtr =/= 0.U), 
        if3_ReqVec(if3_minRASIdx), 
        Mux(
            l1_taken, 
            if3_ReqVec(if3_l1_btb_resp.hitIndex), 
            Mux(
                use_l0_pred, 
                if3_ReqVec(if3_l0_btb_resp.hitIndex), 
                if3_ReqVec(firstValidIndex)
            )
        )
    )

    // l0 btb hit but if3 not jump, false, not a branch
    if3_redirect.isCall := false.B
    if3_redirect.isReturn := false.B

    // l1 btb hit / not hit reset rasTop to origin
    if3_redirect.rasTopPtr := if3_rasTopPtr

    l0_btb.io.update := if3_l0_btb_update

    // mask behind hitIndex items
    // if0 hit is a mistake, mask behind if0 hitindex items(include if0 hitindex)
    val if3_ReqValid_o = WireInit(VecInit(
        Seq.fill(p(FETCH_WIDTH))(false.B)
    ))

    for(i <- 0 until p(FETCH_WIDTH)) {
        when(
            (if3_rasIsCall && if3_minRASIdx < i.U) ||
            (if3_rasIsReturn && if3_minRASIdx < i.U) ||
            (l1_taken && if3_l1_btb_resp.hitIndex < i.U) ||
            (if3_l0_btb_resp.valid && if3_l0_btb_resp.hitIndex < i.U)
        ) {
            if3_ReqValid_o(i) := false.B
        }.otherwise {
            if3_ReqValid_o(i) := if3_ReqValid(i)
        }
    }

    // if4 stage
    val if4_branch_results = WireInit(VecInit(
        Seq.fill(p(FETCH_WIDTH))(0.U.asTypeOf(new BranchPredInfo))
    ))

    val if4_if3_redirect = PipeReg(if3_redirect, if4_stall, flush)
    val if4_flush = flush

    val if4_l0_btb_resp = PipeReg(if3_l0_btb_resp, if4_stall, flush)
    val if4_l1_btb_resp = PipeReg(if3_l1_btb_resp, if4_stall, flush)

    val if4_ReqVec = PipeReg(if3_ReqVec, if4_stall, flush)
    val if4_ReqValid = PipeReg(if3_ReqValid_o, if4_stall, flush)
    val if4_pred_resp = PipeReg(if3_pred_resp, if4_stall, flush)
    val if4_insts = PipeReg(if3_insts, if4_stall, flush)
    val if4_icache_hit = PipeReg(if3_icache_hit, if4_stall, flush)
    val if4_rasTopPtr = PipeReg(if3_rasTopPtr, if4_stall, flush)

    val if4_mshr_id = PipeReg(if3_mshr_id, if4_stall, flush)
    val if4_mshr_id_valid = PipeReg(if3_mshr_id_valid, if4_stall, flush)

    val if4_noRedirectIsCall = Mux(if4_l0_btb_resp.valid, if4_l0_btb_resp.isCall, false.B)
    val if4_noRedirectIsReturn = Mux(if4_l0_btb_resp.valid, if4_l0_btb_resp.isReturn, false.B)
    val if4_noRedirectRasTopPtr = if4_rasTopPtr

    val if4_pa = PipeReg(if3_pa, if4_stall, flush)

    for(i <- 0 until p(FETCH_WIDTH)) {
        if4_branch_results(i).valid := if4_ReqValid(i)
        if4_branch_results(i).counter := if4_pred_resp(i).counter
        if4_branch_results(i).ghist := if4_pred_resp(i).ghist
        if4_branch_results(i).lhist := if4_pred_resp(i).lhist
        // l1 btb hit && jump -> l1 btb target
        if4_branch_results(i).target := Mux(
            if4_l1_btb_resp.valid && if4_l1_btb_resp.hitIndex === i.U && if4_pred_resp(i).counter(counterBits - 1),
            if4_l1_btb_resp.target,
            if4_ReqVec(i) + 4.U
        )
        if4_branch_results(i).isCall := Mux(if4_if3_redirect.redirect, if4_if3_redirect.isCall, if4_noRedirectIsCall)
        if4_branch_results(i).isReturn := Mux(if4_if3_redirect.redirect, if4_if3_redirect.isReturn, if4_noRedirectIsReturn)
        if4_branch_results(i).rasTopPtr := Mux(if4_if3_redirect.redirect, if4_if3_redirect.rasTopPtr, if4_noRedirectRasTopPtr)
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
    inst_buffer.io.if4_mshr_id_i := if4_mshr_id
    inst_buffer.io.if4_mshr_id_valid_i := if4_mshr_id_valid
    inst_buffer.io.if4_icache_hit_i := if4_icache_hit

    inst_buffer.io.refill_valid_i := io.if5_icache_refill_resp.valid
    inst_buffer.io.refill_pa_i := io.if5_icache_refill_resp.pa
    inst_buffer.io.refill_insts_i := io.if5_icache_refill_resp.data
    inst_buffer.io.refill_mshr_id_i := io.if5_mshr_id
    inst_buffer.io.refill_mshr_id_valid_i := io.if5_mshr_id_valid

    // if4 stage deq, if5 get deq item
    io.if5_inst := inst_buffer.io.if5_inst_o
    io.if5_pc_vec := inst_buffer.io.if5_pc_o
    io.if5_instValid := inst_buffer.io.if5_instValid_o
    io.if5_br_pred_info_vec := inst_buffer.io.if5_branch_result_o

    val if3_l1_btb_hit = PipeReg(l1_btb.io.if2_l1_btb_hit, if3_stall, if3_flush)
    val if4_l1_btb_hit = PipeReg(if3_l1_btb_hit, if4_stall, flush)
    val if5_l1_btb_hit = PipeReg(if4_l1_btb_hit, io.stall_from_backpipe, flush)

    val if4_predecode = PipeReg(if3_predecode, if4_stall, flush)
    val if5_predecode = PipeReg(if4_predecode, io.stall_from_backpipe, flush)

    when(if1_l0_btb_hit.asUInt.orR) {
        l0btbhitCount := l0btbhitCount + PopCount(if1_l0_btb_hit)
    }

    when(if5_l1_btb_hit.asUInt.orR) {
        val l1_btb_hit_mask = WireInit(VecInit(
            Seq.fill(p(FETCH_WIDTH))(false.B)
        ))
        for(i <- 0 until p(FETCH_WIDTH)) {
            l1_btb_hit_mask(i) := 
                (if5_predecode(i).isCall || if5_predecode(i).isReturn || if5_predecode(i).isConditionalBranch) && 
                if5_l1_btb_hit(i) &&
                io.if5_instValid(i)
        }
        l1btbhitCount := l1btbhitCount + PopCount(l1_btb_hit_mask)
    }

    io.pushCount := ras.io.pushCount
    io.popCount := ras.io.popCount
}
