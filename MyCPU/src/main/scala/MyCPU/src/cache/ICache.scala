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
        // if0 stage, use page offset as index, only one req
        val if0_icache_req = Input(new ICacheReadReq)
        // if1 stage, use ppn to judge hit
        val if1_itlb_resp = Input(Vec(p(FETCH_WIDTH), new ITLBRespInfo))
        // to memory, axi
        val axi4_master = new AXI4(pa_bits, p(DATA_WIDTH), 4)
        // resp
        val if1_icacheline_resp = Output(Vec(p(L1CacheLineLength), UInt(p(DATA_WIDTH).W)))
        val if1_icache_hit = Output(Bool())
        // stall signal
        val if1_icache_stall_req = Output(Bool())        
    })

    val sram = Module(new ICacheSram)
    val refiller = Module(new ICacheRefiller(p(ICacheMSHRNum)))
    val policy = Module(new pLRUReplacePolicy(1, p(L1CacheWayNum), depth))

    
}