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
    // use l0 btb res & l1 btb res to generate next pred target
    // use final pred target to access RAS, piplined l0 pred result
    // l0 jump but l1 not jump, not save icache request, redirect to l1 pred target
    val io = IO(new Bundle{
        val commit_redirect = Input(new CommitUpdateInfo)
        // from commit stage
        val l1_btb_update = Input(Vec(p(FETCH_WIDTH), new L1BTBUpdateInfo))
        val predictor_update = Input(Vec(p(FETCH_WIDTH), new PredictorUpdateInfo))
        // to commit stage
        val wb1_l1_btb_enqReady = Output(Bool())
        val wb1_predictor_enqReady = Output(Bool())
        // from decode stage
        // to iTLB
        // from iTLB
        // to icache
        // from icache
        // to middle end
    })

    val l0_btb = Module(new L0BTB)
    val l1_btb = Module(new L1BTB)
    val predictor = Module(new GSharePredictor)
    val ras = Module(new RAS)

    val l1_btb_wb_queue = Module(new L1BTBWbQueue(32))
    val pred_wb_queue = Module(new PredictorWbQueue(32))

    // to do: inst buffer

    val offsetBits = 2
    val fetchBits = log2Ceil(p(FETCH_WIDTH))
    val rasPtrWidth = log2Ceil(p(RASParam).RASSize + 1)
    val counterBits = 2

    // prio: commit > decode > if1_redirect > if0_redirect
    val if1_redirect = WireInit(0.U.asTypeOf(new IF1RedirectInfo))
    val if0_redirect = WireInit(0.U.asTypeOf(new L0BTBRespInfo))

    // wb1 stage
    val wb1_l1_btb_update = WireInit(VecInit(
        Seq.fill(p(FETCH_WIDTH))(0.U.asTypeOf(new L1BTBUpdateInfo))
    ))
    val wb1_pred_update = WireInit(VecInit(
        Seq.fill(p(FETCH_WIDTH))(0.U.asTypeOf(new PredictorUpdateInfo))
    ))

    l1_btb_wb_queue.io.enq := io.l1_btb_update
    pred_wb_queue.io.enq := io.predictor_update

    io.wb1_l1_btb_enqReady := l1_btb_wb_queue.io.enqReady
    io.wb1_predictor_enqReady := pred_wb_queue.io.enqReady

    l1_btb.io.update := l1_btb_wb_queue.io.deq
    predictor.io.update := pred_wb_queue.io.deq

    // if0 stage
    val pc = RegInit(0.U(p(ADDR_WIDTH).W))
    val nextPC = WireInit(0.U(p(ADDR_WIDTH).W))
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

    for(i <- 0 until p(FETCH_WIDTH)) {
        val pc_loc = pc(p(ADDR_WIDTH) - 1, offsetBits + fetchBits) | (i << offsetBits).U
        if0_ReqVec(i) := pc_loc
        if0_ReqValid(i) := ~(pc(offsetBits + fetchBits, offsetBits) > i.U)
    }

    l0_btb.io.req.zipWithIndex.map(
        {
            case (item, index) => {
                item.pc := if0_ReqVec(index)
                item.valid := if0_ReqValid(index)
            }
        }
    )
    l0_btb.io.rasTopPtr := ras.io.rasTopPtr

    l1_btb.io.req.zipWithIndex.map(
        {
            case (item, index) => {
                item.pc := if0_ReqVec(index)
                item.valid := if0_ReqValid(index)
            }
        }
    )

    predictor.io.if0_req.zipWithIndex.map(
        {
            case (item, index) => {
                item.pc := if0_ReqVec(index)
                item.valid := if0_ReqValid(index)
            }
        }
    )

    when(io.commit_redirect.flush) {
        nextPC := io.commit_redirect.flush_pc
        nextIsCall := io.commit_redirect.isCall
        nextIsReturn := io.commit_redirect.isReturn
        nextRASTop := io.commit_redirect.rasTopPtr
        rasUpdateData := Mux(io.commit_redirect.isCall, io.commit_redirect.flush_pc, 0.U)
    }
    // to do: decode redirect
    .elsewhen(if1_redirect.redirect) {
        nextPC := if1_redirect.target
        nextIsCall := if1_redirect.isCall
        nextIsReturn := if1_redirect.isReturn
        nextRASTop := if1_redirect.rasTopPtr
        rasUpdateData := Mux(if1_redirect.isCall, if1_redirect.target, 0.U)
    }
    .elsewhen(if0_redirect.valid) {
        nextPC := if0_redirect.target
        nextIsCall := if0_redirect.isCall
        nextIsReturn := if0_redirect.isReturn
        nextRASTop := if0_redirect.rasTopPtr
        rasUpdateData := Mux(if0_redirect.isCall, if0_redirect.target, 0.U)        
    }
    if0_redirect := l0_btb.io.resp
    
    ras.io.next_ras_top := nextPC
    ras.io.next_ras_top_ptr := nextRASTop
    ras.io.next_ras_top_valid := nextIsCall

    // if1 stage
    val if1_rasTopPtr = RegInit(0.U(rasPtrWidth.W))
    val if1_rasTop = RegInit(0.U(p(ADDR_WIDTH).W))
    val if1_ReqVec = RegNext(if0_ReqVec)
    val if1_ReqValid = RegNext(if0_ReqValid)

    val if1_l0_btb_resp = RegNext(l0_btb.io.resp)
    val if1_l0_btb_update = WireInit(0.U.asTypeOf(new L0BTBUpdateInfo))

    val if1_l1_btb_resp = l1_btb.io.resp
    val if1_pred_resp = predictor.io.if1_predict

    // get if1 redirect & l0 btb update infos
    // l1 btb hit different to l0 btb, l0 btb hit but different to l1, l0 btb not hit
    if1_l0_btb_update.valid := if1_l1_btb_resp.valid && (
        (if1_l1_btb_resp.target =/= if1_l0_btb_resp.target && if1_l0_btb_resp.valid) ||
        (~if1_l0_btb_resp.valid)
    )
    if1_l0_btb_update.pc := if1_ReqVec(if1_l1_btb_resp.hitIndex)
    if1_l0_btb_update.isCall := if1_l1_btb_resp.isCall
    if1_l0_btb_update.isReturn := if1_l1_btb_resp.isReturn
    if1_l0_btb_update.target := if1_l1_btb_resp.target

    val predResult = if1_pred_resp(if1_l1_btb_resp.hitIndex).counter(counterBits - 1)
    val predValid = if1_pred_resp(if1_l1_btb_resp.hitIndex).valid

    if1_redirect.redirect := (if1_l0_btb_resp.valid ^ (if1_l1_btb_resp.valid && predResult && predValid)) ||
        (if1_l0_btb_resp.target =/= if1_l1_btb_resp.target) ||
        (if1_l0_btb_resp.isCall ^ if1_l1_btb_resp.isCall) ||
        (if1_l0_btb_resp.isReturn ^ if1_l0_btb_resp.isReturn)

    if1_redirect.target := Mux(
        if1_l1_btb_resp.valid && predResult && predValid, 
        if1_l1_btb_resp.target,
        if1_ReqVec(p(FETCH_WIDTH) - 1) + (p(FETCH_WIDTH) << offsetBits).U
    )
    if1_redirect.isCall := Mux(
        if1_l1_btb_resp.valid && predResult && predValid,
        if1_l1_btb_resp.isCall,
        false.B
    )
    if1_redirect.isReturn := Mux(
        if1_l1_btb_resp.valid && predResult && predValid,
        if1_l1_btb_resp.isReturn,
        false.B
    )
    if1_redirect.rasTopPtr := Mux(
        if1_l1_btb_resp.valid && predResult && predValid,
        if1_rasTop + if1_l1_btb_resp.isCall - if1_l1_btb_resp.isReturn,
        if1_rasTop        
    )
    l0_btb.io.update := if1_l0_btb_update
}
