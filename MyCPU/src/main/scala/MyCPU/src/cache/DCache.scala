package MyCPU.cache

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._

// store 
// mem0: access dcache, send to bank access buffer, access store buffer write va and data to write
// mem1: write to store waiting queue
// mem3: get dcache hit infos, waiting queue each item judge for each bank result
// 
class DCache(implicit p: Parameters) extends Module {
    val readPortNum = p(LDU_NUM)
    val writePortNum = p(STU_NUM)
    val bankNum = writePortNum + readPortNum
    // use writePortNum as bankNum
    // copy readPortNum within a bank
    val bankBits = log2Ceil(bankNum)
    
    val io = IO(new Bundle {
        val flush = Input(Bool())
        val stall_from_backpipe = Input(Bool())
        // mem0 stage, pre read cacheline and tagV(both need read cacheline)
        val mem0_readReqs = Input(Vec(p(LDU_NUM), new DCacheReq))
        val mem0_writeReqs = Input(Vec(p(STU_NUM), new DCacheReq))
        // mem2 stage, dtlb resps
        val mem2_dtlb_resps = Input(Vec(bankNum, new DTLBRespInfo))
        // mem2 stage send hit info, mem4 all insts get hit message
        val mem2_dcache_hit_infos = Output(Vec(bankNum, new DCacheHitInfo))
        // have to stall all pipelines, because have full skidbuffer
        val mem0_bankconflict_stall = Output(Bool())     
        // judge bank output, may need to stall a pipeline
        // use bank output to access dtlb at mem1
        val mem1_dtlb_reqs = Output(Vec(bankNum, new DTLBReqInfo))
        // tlb miss, stall
        val mem2_dtlb_miss_stall = Output(Bool())
        // stall when mshr queue can not enq
        val mem3_mshr_stall = Output(Bool())
        // write back stall
        val mem4_dcache_wb_stall = Output(Bool())
        // mem4 change banked data to pipelines
        val mem4_dcache_ld_valid = Output(Vec(p(LDU_NUM), Bool()))
        val mem4_dcache_ld_data = Output(Vec(p(LDU_NUM), UInt(p(DATA_WIDTH).W)))

    })

    val sram = Module(new DCacheSram)
    val policy = Module(new pLRUReplacePolicy(bankNum, p(L1CacheWayNum)))
    val refiller = Module(new DCacheRefiller(p(DCacheMSHRNum)))
}