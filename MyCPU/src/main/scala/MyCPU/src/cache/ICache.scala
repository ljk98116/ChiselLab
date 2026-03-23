package MyCPU.cache

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._

class ICache(implicit p: Parameters) extends Module {
    val pa_bits = (new PhysicalAddress).GetWidth
    val depth = 1 << p(ICacheIndexBits)
    val io = IO(new Bundle {
        // flushs
        val commit_flush = Input(Bool())
        val if2_flush = Input(Bool())
        val if1_flush = Input(Bool())
        // stalls
        val if2_stall = Input(Bool())
        val if1_stall = Input(Bool())
        // if0 stage, use page offset as index, only one req
        val if0_icache_req = Input(new ICacheReadReq)
        // if1 stage, use ppn to judge hit
        val if1_itlb_resp = Input(Vec(p(FETCH_WIDTH), new ITLBRespInfo))
        // to memory, axi
        val axi4_master = new AXI4(pa_bits, p(DATA_WIDTH), 4)
        // resp
        val if2_icacheline_resp = Output(Vec(p(L1CacheLineLength), UInt(p(DATA_WIDTH).W)))
        val if2_icache_hit = Output(Bool())
        val if2_predecode = Output(Vec(p(FETCH_WIDTH), new ICacheBranchMetaInfo))
        // stall signal
        val if2_icache_stall_req = Output(Bool())        
    })

    val sram = Module(new ICacheSram)
    val refiller = Module(new ICacheRefiller(p(ICacheMSHRNum)))
    val policy = Module(new pLRUReplacePolicy(1, p(L1CacheWayNum)))

    // sram <> io
    sram.io.if0_icache_req := io.if0_icache_req
    sram.io.if1_itlb_resp := io.if1_itlb_resp
    sram.io.commit_flush := io.commit_flush
    sram.io.if1_flush := io.if1_flush
    sram.io.if2_flush := io.if2_flush
    sram.io.if1_stall := io.if1_stall
    sram.io.if2_stall := io.if2_stall

    io.if2_icache_stall_req := sram.io.if2_icache_stall_req
    io.if2_icacheline_resp := sram.io.if2_icacheline_resp
    io.if2_icache_hit := sram.io.if2_icache_hit
    io.if2_predecode := sram.io.if2_predecode

    // sram <> refiller
    sram.io.if2_mshr_enqReady := refiller.io.if2_mshr_enqReady
    sram.io.icache_refill_resp := refiller.io.icache_refill_resp
    refiller.io.if1_icache_refill_req := sram.io.if2_icache_refill_req

    // sram <> policy
    sram.io.icacheReplaceRefillResp := policy.io.refillResp
    policy.io.hitUpdate(0) := sram.io.icacheReplaceHitUpdate
    policy.io.refillReq := refiller.io.icacheReplaceRefillReq
    policy.io.refillUpdate := sram.io.icacheRefillUpdate

    // refiller <> io
    io.axi4_master <> refiller.io.axi4_master
}