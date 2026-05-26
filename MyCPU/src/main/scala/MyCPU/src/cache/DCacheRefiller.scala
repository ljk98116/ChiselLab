package MyCPU.cache

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._
import MyCPU.cache._

// use dcache write buffer reqs to fill write channels
// parallel dealing read and write
class DCacheRefiller(implicit p: Parameters) extends Module {
    val pa_bits = (new PhysicalAddress).GetWidth
    val io = IO(new Bundle {
        // from dcache wb buffer
        val mem3_dcache_wb_req = Input(new DCacheWriteBackReq)
        // from mshr queue
        val mem2_refill_req = Input(new CacheRefillReq)
        // to replace policy module
        val dcacheReplaceRefillReq = Output(new ReplacePolicyRefillReqInfo)
        // to icache sram
        val dcache_refill_resp = Output(new DCacheRefillResp)
        val mem2_mshr_enqReady = Output(Bool())
        // as master send req to crossbar
        val axi4_master = new AXI4(pa_bits, p(DATA_WIDTH), 4)        
    })

    
}

