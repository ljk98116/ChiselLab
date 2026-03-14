package MyCPU.cache

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._

// 1 port access is enough
// if1 judge hit -> judge mshr merge
class ICacheSram(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        // if0 stage, use page offset as index, only one req
        val if0_icache_req = Input(new ICacheReadReq)
        // if1 stage, use ppn to judge hit
        val if1_itlb_resp = Input(Vec(p(FETCH_WIDTH), new ITLBRespInfo))
        // refill req
        val if1_icache_refill_req = Output(new ICacheRefillReq)
        val if1_mshr_enqReady = Input(Bool())
        // refill resp
        val icache_refill_resp = Input(new ICacheRefillResp)
        // to replace policy module req
        val icacheReplaceHitUpdate = Output(new ReplacePolicyHitUpdateInfo)
        // from replace policy module resp
        val icacheReplaceRefillResp = Input(new ReplacePolicyRefillRespInfo)
        // to replace policy module
        val icacheRefillUpdate = Output(new ReplacePolicyRefillUpdateInfo)
        // resp
        val if1_icacheline_resp = Output(Vec(p(L1CacheLineLength), UInt(p(DATA_WIDTH).W)))
        val if1_icache_hit = Output(Bool())
        // stall signal
        val if1_icache_stall_req = Output(Bool())
    })

    val mem = Seq.fill(p(L1CacheWayNum))(
        SyncReadMem((1 << p(ICacheIndexBits)), new ICacheLineEntry)
    )

    // if1 icache hit, return hit and data
    // if1 icache miss, use itlb resp to construct mshr, if missqueue not full or refill req can merge, send refill req
    val if1_icache_entries = mem.map(_.read(io.if0_icache_req.index))
    // itlb must hit
    val if1_icache_access_valid = 
        RegNext(io.if0_icache_req.valid) && 
        io.if1_itlb_resp(0).valid && 
        io.if1_itlb_resp(0).hit

    val if1_hitArray = if1_icache_entries.map(
        entry => entry.valid && entry.tag === io.if1_itlb_resp(0).paddr.GetPPN
    )
    val if1_icache_req = RegNext(io.if0_icache_req)

    io.if1_icache_hit := if1_hitArray.reduce(_||_)
    val if1_hitwayIndex = PriorityEncoder(if1_hitArray)
    val memReadResult = VecInit((0 until p(L1CacheWayNum)).map(
        index => mem(index).read(io.if0_icache_req.index)
    ))

    // mshr construct
    io.if1_icache_refill_req.valid := !io.if1_icache_hit && if1_icache_access_valid
    io.if1_icache_refill_req.pa := Cat(
        io.if1_itlb_resp(0).paddr.GetPPN, if1_icache_req.index, 0.U((p(L1CacheOffsetBits) + p(FETCH_OFFSET)).W)
    ).asTypeOf(new PhysicalAddress)

    // need refill, mshr queue full and can not merge
    io.if1_icache_stall_req := !io.if1_mshr_enqReady && io.if1_icache_refill_req.valid
    io.if1_icacheline_resp := memReadResult(if1_hitwayIndex).data

    io.icacheRefillUpdate.valid := io.icacheReplaceRefillResp.valid
    io.icacheRefillUpdate.way := io.icacheReplaceRefillResp.way
    io.icacheRefillUpdate.index := io.icache_refill_resp.pa.bits(
        p(ICacheIndexBits) + p(L1CacheOffsetBits) + p(FETCH_OFFSET) - 1,
        p(L1CacheOffsetBits) + p(FETCH_OFFSET)
    )

    io.icacheReplaceHitUpdate.valid := if1_hitArray.reduce(_||_)
    io.icacheReplaceHitUpdate.way := if1_hitwayIndex
    io.icacheReplaceHitUpdate.index := if1_icache_req.index
    // refill icache
    (0 until p(L1CacheWayNum)).map(
        wayIndex => {
            when(io.icacheReplaceRefillResp.way === wayIndex.U) {
                val data = io.icache_refill_resp.data
                val tag = io.icache_refill_resp.pa.GetPPN
                val index = io.icache_refill_resp.pa.bits(
                    p(ICacheIndexBits) + p(L1CacheOffsetBits) + p(FETCH_OFFSET) - 1,
                    p(L1CacheOffsetBits) + p(FETCH_OFFSET)
                )
                val insertItem = WireInit(0.U.asTypeOf(new ICacheLineEntry))
                insertItem.data := data
                insertItem.valid := true.B
                insertItem.tag := tag
                mem(wayIndex).write(
                    index, insertItem
                )
            }
        }
    )
}