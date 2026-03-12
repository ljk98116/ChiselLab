package MyCPU.cache

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._

// if1 judge if mshr item can merge
// do replace policy read when at sRead's last cycle
// access axi4 memory
class ICacheRefiller(MSHRQueueSize: Int)(implicit p: Parameters) extends Module {
    val pa_bits = (new PhysicalAddress).GetWidth
    val io = IO(new Bundle {
        // from icache
        val if1_icache_refill_req = Input(new ICacheRefillReq)
        // to icache
        val icache_refill_resp = Output(new ICacheRefillResp)
        val if1_mshr_enqReady = Output(Bool())
        // to replace policy module
        val icacheRefillUpdate = Output(new ReplacePolicyRefillUpdateInfo)
        // as master send req to crossbar
        val axi4_master = new AXI4(pa_bits, p(DATA_WIDTH), 4)
    })

    val mshr_buffer = Module(new MSHRQueue(MSHRQueueSize))

    val refill_start = WireInit(false.B)
    val refill_pa = RegInit(0.U.asTypeOf(new PhysicalAddress))

    val burst_len = p(L1CacheLineLength)

    mshr_buffer.io.enq := io.if1_icache_refill_req.asTypeOf(new CacheRefillReq)
    // sIdle read MSHRQueue deq
    mshr_buffer.io.deqValid := (state === sIdle)

    io.if1_mshr_enqReady := mshr_buffer.io.enqReady

    refill_start := mshr_buffer.io.deqReady
    refill_pa := mshr_buffer.io.deq.pa

    val sIdle :: sAddr :: sRead :: sResp :: Nil = Enum(4)
    val state = RegInit(sIdle)

    // IDLE -> AR -> R -> Resp -> IDLE
    val cacheLineBuffer = RegInit(VecInit(
        Seq.fill(burst_len)(0.U(p(DATA_WIDTH).W))
    ))

    val beatCnt = RegInit(0.U((p(L1CacheOffsetBits) + 1).W))

    // AR Channel
    io.axi4_master.ar.bits.id := 0.U
    io.axi4_master.ar.bits.burst := 1.U
    io.axi4_master.ar.bits.addr := refill_pa
    io.axi4_master.ar.bits.len := 7.U
    io.axi4_master.ar.bits.size := log2Ceil(p(DATA_WIDTH) / 8).U

    io.axi4_master.ar.valid := (state === sAddr)

    // R Channel
    io.axi4_master.r.ready := (state === sRead)

    // resp
    io.icache_refill_resp.valid := (state === sResp)
    io.icache_refill_resp.data := cacheLineBuffer
    io.icache_refill_resp.pa := refill_pa

    // FSM
    switch(state) {
        is(sIdle) {
            when(refill_start) {
                state := sAddr
            }
        }
        is(sAddr) {
            when(io.axi4_master.ar.fire) {
                state := sRead
            }
        }
        is(sRead) {
            when(io.axi4_master.r.fire) {
                cacheLineBuffer(beatCnt) := io.axi4_master.r.bits.data
                beatCnt := beatCnt + 1.U
                when(io.axi4_master.r.bits.last) {
                    state := sResp
                }
            }
        }
        is(sResp) {
            beatCnt := 0.U
            state := sIdle
        }
    }
}