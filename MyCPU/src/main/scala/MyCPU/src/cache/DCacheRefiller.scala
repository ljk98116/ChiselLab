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
class DCacheRefiller(MSHRQueueSize: Int)(implicit p: Parameters) extends Module {
    val pa_bits = (new PhysicalAddress).GetWidth
    val readPortNum = p(LDU_NUM)
    val writePortNum = p(STU_NUM)
    val bankNum = writePortNum + readPortNum

    val io = IO(new Bundle {
        val flush = Input(Bool())
        // from dcache wb buffer
        val mem4_wb_req = Input(new DCacheWriteBackReq)
        // from dcache sram
        val mem3_dcache_refill_req = Input(Vec(bankNum, new CacheRefillReq))
        val mem3_dcache_mshr_enqReady = Output(Bool())
        // to replace policy module
        val dcacheReplaceRefillReq = Output(new ReplacePolicyRefillReqInfo)
        // store buffer deq, when cacheline write back
        val storeBufferDeqEnable = Output(Bool())
        // to dcache sram
        val dcache_refill_resp = Output(new DCacheRefillResp)
        // as master send req to crossbar
        val axi4_master = new AXI4(pa_bits, p(DATA_WIDTH), 4)        
    })

    val mshr_buffer = Module(new DCacheMSHRQueue(MSHRQueueSize))

    val refill_start = WireInit(false.B)
    val refill_pa = RegInit(0.U.asTypeOf(new PhysicalAddress))

    val write_back_start = WireInit(false.B)
    val write_back_pa = RegInit(0.U.asTypeOf(new PhysicalAddress))
    
    // read fsm[handle cache miss r/w]: IDLE -> AR -> R -> Resp -> IDLE
    val s_read_idle::s_read_raddr::s_read_rread::s_read_rresp::Nil = Enum(4)
    val rState = RegInit(s_read_idle)
    val burst_len = p(L1CacheLineLength)

    val rCacheLineBuffer = RegInit(VecInit(
        Seq.fill(burst_len)(0.U(p(DATA_WIDTH).W))
    ))

    val rBeatCnt = RegInit(0.U(p(L1CacheOffsetBits).W))

    // write fsm[handle store write back]: 
    val s_write_idle::s_write_waddr::s_write_wdata::s_write_wresp::Nil = Enum(4)
    val wState = RegInit(s_write_idle)
    val wBeatCnt = RegInit(0.U(p(L1CacheOffsetBits).W))
    val wCacheLineBuffer = RegInit(VecInit(
        Seq.fill(burst_len)(0.U(p(DATA_WIDTH).W))
    ))

    mshr_buffer.io.mem3_enqs := io.mem3_dcache_refill_req
    // s_read_idle read MSHRQueue deq
    mshr_buffer.io.deqEnable := (rState === s_read_idle)
    mshr_buffer.io.flush := io.flush

    io.mem3_dcache_mshr_enqReady := mshr_buffer.io.mem3_enqReady

    refill_start := mshr_buffer.io.deq.valid
    refill_pa := mshr_buffer.io.deq.pa

    // AR Channel
    io.axi4_master.ar.bits.id := 1.U
    io.axi4_master.ar.bits.burst := 1.U
    io.axi4_master.ar.bits.addr := refill_pa.bits
    io.axi4_master.ar.bits.len := 3.U
    io.axi4_master.ar.bits.size := log2Ceil(p(DATA_WIDTH) / 8).U

    io.axi4_master.ar.valid := (rState === s_read_raddr)

    // R Channel    
    io.axi4_master.r.ready := rState === s_read_rread

    // R resp
    io.dcache_refill_resp.valid := rState === s_read_rresp
    io.dcache_refill_resp.data := rCacheLineBuffer
    io.dcache_refill_resp.pa := refill_pa

    io.dcacheReplaceRefillReq.valid := rState === s_read_rresp
    io.dcacheReplaceRefillReq.index := refill_pa.bits(
        p(ICacheIndexBits) + p(L1CacheOffsetBits) + p(FETCH_OFFSET) - 1,
        p(L1CacheOffsetBits) + p(FETCH_OFFSET)        
    )

    // read fsm
    switch(rState) {
        is(s_read_idle) {
            when(refill_start) {
                rState := s_read_raddr
            }
        }
        is(s_read_raddr) {
            when(io.axi4_master.ar.fire) {
                rState := s_read_rread
            }
        }
        is(s_read_rread) {
            when(io.axi4_master.r.fire) {
                rCacheLineBuffer(rBeatCnt) := io.axi4_master.r.bits.data
                rBeatCnt := rBeatCnt + 1.U
                when(io.axi4_master.r.bits.last) {
                    rState := s_read_rresp
                }
            }
        }
        is(s_read_rresp) {
            rBeatCnt := 0.U
            rState := s_read_idle
        }
    }

    // now store buffer can deq
    io.storeBufferDeqEnable := io.mem4_wb_req.valid

    write_back_start := io.mem4_wb_req.valid
    write_back_pa := io.mem4_wb_req.pa

    // AW Channel
    io.axi4_master.aw.bits.id := 1.U
    io.axi4_master.aw.bits.addr := write_back_pa.bits
    io.axi4_master.aw.bits.burst := 1.U
    io.axi4_master.aw.bits.len := 3.U
    io.axi4_master.aw.bits.size := log2Ceil(p(DATA_WIDTH) / 8).U

    io.axi4_master.aw.valid := wState === s_write_waddr

    // W Channel
    io.axi4_master.w.bits.data := Mux(
        wState === s_write_wdata, 
        wCacheLineBuffer(wBeatCnt),
        0.U
    )

    io.axi4_master.w.bits.last := 
        wBeatCnt === (p(L1CacheLineLength) - 1).U &&
        wState === s_write_wdata

    io.axi4_master.w.bits.strb := Mux(
        wState === s_write_wdata, 
        "b1111".U,
        0.U        
    )
    io.axi4_master.w.valid := wState === s_write_wdata

    // B Channel
    io.axi4_master.b.ready := io.axi4_master.b.valid

    switch(wState) {
        is(s_write_idle) {
            when(write_back_start) {
                wState := s_write_waddr
                // prepare cacheLine
                for(l <- 0 until p(L1CacheLineLength)) {
                    wCacheLineBuffer(l) := 
                        io.mem4_wb_req.cacheLine.datas(l)
                }
            }
        }
        is(s_write_waddr) {
            when(io.axi4_master.aw.fire) {
                wState := s_write_wdata
            }
        }
        is(s_write_wdata) {
            when(io.axi4_master.w.fire) {
                wBeatCnt := wBeatCnt + 1.U
                when(wBeatCnt === (p(L1CacheLineLength) - 1).U) {
                    wState := s_write_wresp
                }
            }
        }
        is(s_write_wresp) {
            when(io.axi4_master.b.fire) {
                wState := s_write_idle
                wBeatCnt := 0.U
            }
        }
    }
}

