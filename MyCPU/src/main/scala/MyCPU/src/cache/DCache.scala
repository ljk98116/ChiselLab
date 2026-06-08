package MyCPU.cache

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

class DCache(implicit p: Parameters) extends Module {
    val readPortNum = p(LDU_NUM)
    val writePortNum = p(STU_NUM)
    val bankNum = writePortNum + readPortNum
    // use writePortNum as bankNum
    // copy readPortNum within a bank
    val bankBits = log2Ceil(bankNum)
    val pa_bits = (new PhysicalAddress).GetWidth
    val io = IO(new Bundle {
        val flush = Input(Bool())
        // mem0 stage, pre read cacheline and tagV(both need read cacheline)
        val mem0_bank_reqs = Input(Vec(bankNum, new DCacheReq))
        // mem2 stage, dtlb resps
        val mem2_dtlb_resps = Input(Vec(bankNum, new DTLBRespInfo))
        // judge bank output, may need to stall a pipeline
        // use bank output to access dtlb at mem1
        val mem1_dtlb_reqs = Output(Vec(bankNum, new DTLBReqInfo))
        // tlb miss, stall
        val mem2_dtlb_miss_stall = Output(Bool())
        // stall when mshr queue can not enq
        val mem3_mshr_stall = Output(Bool())
        val mem3_dcache_hitInfos = Output(Vec(bankNum, new DCacheHitInfo))
        // stall when waiting queue can not enq
        val mem4_stall = Input(Bool())
        val mem4_wb_req = Input(new DCacheWriteBackReq)
        val storeBufferDeqEnable = Output(Bool()) 
        val axi4_master = new AXI4(pa_bits, p(DATA_WIDTH), 4)
    })

    val sram = Module(new DCacheSram)
    val policy = Module(new pLRUReplacePolicy(bankNum, p(L1CacheWayNum)))
    val refiller = Module(new DCacheRefiller(p(DCacheMSHRNum)))

    // sram io
    sram.io.flush := io.flush
    sram.io.mem0_bank_reqs := io.mem0_bank_reqs
    sram.io.mem2_dtlb_resps := io.mem2_dtlb_resps
    io.mem1_dtlb_reqs := sram.io.mem1_dtlb_reqs
    io.mem2_dtlb_miss_stall := sram.io.mem2_dtlb_miss_stall
    io.mem3_mshr_stall := sram.io.mem3_mshr_stall
    io.mem3_dcache_hitInfos := sram.io.mem3_dcache_hitInfos

    sram.io.dcacheRefillResp := refiller.io.dcache_refill_resp
    sram.io.mem3_dcache_mshr_enqReady := refiller.io.mem3_dcache_mshr_enqReady
    refiller.io.mem3_dcache_refill_req := sram.io.mem3_refill_reqs

    sram.io.dcacheReplaceRefillResp := policy.io.refillResp
    policy.io.hitUpdate := sram.io.dcacheReplaceHitUpdate
    policy.io.refillUpdate := sram.io.dcacheRefillUpdate

    // refiller io
    refiller.io.flush := io.flush
    refiller.io.axi4_master <> io.axi4_master
    io.storeBufferDeqEnable := refiller.io.storeBufferDeqEnable
    refiller.io.mem4_wb_req := io.mem4_wb_req

    policy.io.refillReq := refiller.io.dcacheReplaceRefillReq
}