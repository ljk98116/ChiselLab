package MyCPU.frontend

import chisel3._
import chisel3.util._

import MyCPU.config._
import MyCPU.io._
import MyCPU.frontend.BPU._
import MyCPU.frontend.BPU.predictors._
import org.chipsalliance.cde.config._

// if0 stage: access ITLB/ L0 / L1 BTB, generate fetch pc and fetch request for if1 stage
class IF0Stage(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        // from commit stage
        val commit_flush_update = Input(new CommitUpdateInfo)
        // to itlb
        val itlb_req = Output(Vec(p(FETCH_WIDTH), new ITLBReqInfo))
        // to l0 btb
        val l0_btb_req = Output(Vec(p(FETCH_WIDTH), new L0BTBReqInfo))
        // from l0 btb
        val l0_btb_resp = Input(Vec(p(FETCH_WIDTH), new L0BTBRespInfo))
        // save l0_btb_resp for if1 stage, used for if1 redirect and if1 branch prediction
        val l0_btb_resp_out = Output(Vec(p(FETCH_WIDTH), new L0BTBRespInfo))
        // if1 redirect signal from later stage, used to redirect pc to l1_redirect_target, same as flush but has lower priority than flush
        val if1_redirect = Input(new IF1RedirectInfo)
        // from ras
        val ras_top = Input(UInt(p(ADDR_WIDTH).W))
        val ras_top_ptr = Input(UInt(log2Ceil(p(RASParam).RASSize).W))
        val ras_top_valid = Input(Bool())
        // to if1 stage, used as snapshot
        val ras_top_ptr_out = Output(UInt(log2Ceil(p(RASParam).RASSize).W))
        val ras_top_valid_out = Output(Bool())
        // to ras
        val next_ras_top_ptr = Output(UInt(log2Ceil(p(RASParam).RASSize).W))
        val next_ras_top = Output(UInt(p(ADDR_WIDTH).W))
        val next_ras_top_valid = Output(Bool())      
        // soon add if2/if3 redirect signal, used to redirect pc to l1_redirect_target, same as flush but has lower priority than if1_redirect
    })

    val pc = RegInit(0.U(p(ADDR_WIDTH).W))
    val nextpc = WireInit(0.U(p(ADDR_WIDTH).W))

    // align fetch pc to fetch_width * 4 bytes
    val align_mask = ~(1 << log2Ceil(p(FETCH_WIDTH)*4) - 1)
    val if0_pc_vec = WireInit(VecInit.fill(p(FETCH_WIDTH))(0.U(p(ADDR_WIDTH).W)))
    if0_pc_vec := VecInit.tabulate(p(FETCH_WIDTH))(
        i => (pc & align_mask.U) | (i * 4).U
    )

    // if0_valid_vec is used to indicate whether the corresponding if0_pc_vec entry is valid (i.e., not less than the current pc)
    val if0_valid_vec = WireInit(VecInit.fill(p(FETCH_WIDTH))(false.B))
    for(i <- 0 until p(FETCH_WIDTH)) {
        if0_valid_vec(i) := pc >= if0_pc_vec(i)
    }

    // connect with l0 btb
    io.l0_btb_req.zip(if0_pc_vec).zip(if0_valid_vec).foreach{ case (((req, pc), valid)) =>
        req.pc := pc
        req.valid := valid
    }
    io.l0_btb_resp_out := io.l0_btb_resp

    pc := nextpc

    // current ras ptr output to if1 stage as snapshot
    io.ras_top_ptr_out := io.ras_top_ptr
    io.ras_top_valid_out := io.ras_top_valid

    
}