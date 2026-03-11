package MyCPU.cache

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._

// 1 port access is enough
class ICache(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        // if0 stage, use page offset as index, only one req
        val if0_icache_req = Input(new ICacheReadReq)
        // if1 stage, use ppn to judge hit
        val if1_itlb_resp = Input(Vec(p(FETCH_WIDTH), new ITLBRespInfo))
        // refill req
        val if1_icache_refill_req = Output(new ICacheRefillReq)
        // refill resp
        val icache_refill_resp = Input(new ICacheRefillResp)
        // resp
        val if1_icacheline_resp = Output(Vec(p(L1CacheLineLength), UInt(p(DATA_WIDTH).W)))
        val if1_icache_hit = Output(Bool())
    })


}