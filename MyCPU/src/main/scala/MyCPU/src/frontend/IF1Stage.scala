package MyCPU.frontend

import chisel3._
import chisel3.util._

import MyCPU.config._
import MyCPU.io._
import MyCPU.frontend.BPU._
import MyCPU.frontend.BPU.predictors._
import org.chipsalliance.cde.config._

// if1 stage: access L1 BTB, generate branch prediction result for later stage
// if1 stage also receives l0_btb_resp from if0 stage, used for if1 redirect and if1 branch prediction
// if1 stage compare PA & icache tag
class IF1Stage(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val flush = Input(Bool())
        // from icache
        // from itlb
        // from pipelined l0 btb
        val l0_btb_resp = Input(new L0BTBRespInfo)
        // from l1 btb
        val l1_btb_resp = Input(new L1BTBRespInfo)
        // from predictor
        val predictor_resp = Input(new PredictorRespInfo)
        // from if0 stage
        val rasTopPtr = Input(UInt(log2Ceil(p(RASParam).RASSize).W))
        // to if0 stage, used for if1 redirect
        val if1_redirect = Output(new IF1RedirectInfo)
    })

    val taken = io.predictor_resp.taken
    // l0/l1 btb pred different or l0 pred is predicted not taken but l1 pred is taken, then redirect to l1 btb resp target, otherwise no redirect
    io.if1_redirect.redirect :=  
        io.l0_btb_resp.valid ^ (io.l1_btb_resp.valid & taken) ||
        (io.l0_btb_resp.target =/= io.l1_btb_resp.target) ||
        (io.l0_btb_resp.isCall =/= io.l1_btb_resp.isCall) ||
        (io.l0_btb_resp.isReturn =/= io.l1_btb_resp.isReturn)
        
    io.if1_redirect.target := Mux(io.if1_redirect.redirect, io.l1_btb_resp.target, 0.U)
}