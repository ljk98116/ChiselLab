package MyCPU.cache

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

// 1 port access is enough
// if1 judge hit -> judge mshr merge
class ICacheSram(implicit p: Parameters) extends Module {
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
        // refill req
        val if2_icache_refill_req = Output(new CacheRefillReq)
        val if2_mshr_enqReady = Input(Bool())
        // refill resp
        val icache_refill_resp = Input(new ICacheRefillResp)
        // to replace policy module req
        val icacheReplaceHitUpdate = Output(new ReplacePolicyHitUpdateInfo)
        // from replace policy module resp
        val icacheReplaceRefillResp = Input(new ReplacePolicyRefillRespInfo)
        // to replace policy module
        val icacheRefillUpdate = Output(new ICacheReplacePolicyRefillUpdateInfo)
        // resp
        val if2_icacheline_resp = Output(Vec(p(FETCH_WIDTH), UInt(p(DATA_WIDTH).W)))
        val if2_icache_hit = Output(Bool())
        val if2_predecode = Output(Vec(p(FETCH_WIDTH), new ICacheBranchMetaInfo))
        // stall signal
        val if2_icache_stall_req = Output(Bool())
    })

    val predecoder = Module(new PreDecode)
    predecoder.io.inst := io.icache_refill_resp.data

    val metaInfoWidth = (new ICacheBranchMetaInfo).getWidth * p(L1CacheLineLength)
    val singleMetaInfoWidth = (new ICacheBranchMetaInfo).getWidth

    val metaInfoMem = Seq.tabulate(p(L1CacheWayNum))(
        index => {
            new LargeSyncBank(clock, reset, 1 << p(L1CacheWayNum), Vec(p(L1CacheLineLength), new ICacheBranchMetaInfo), "icache_meta_info_mem_way" + index.toString)
        }
    )

    val tagWidth = (new PhysicalAddress).getWidth
    val tagVMem = Seq.tabulate(p(L1CacheWayNum))(
        index => {
            // 256x34
            new LargeSyncBank(clock, reset, (1 << p(ICacheIndexBits)), UInt((p(ICacheTagBits) + 1).W), "icache_tag_mem_way" + index.toString)
        }
    )

    val cacheLineWidth = p(L1CacheLineLength) * p(DATA_WIDTH)
    val dataMem = Seq.tabulate(p(L1CacheWayNum))(
        index => {
            // 256x32x4B
            new LargeSyncBank(clock, reset, (1 << p(ICacheIndexBits)), UInt((cacheLineWidth).W), "icache_data_mem_way" + index.toString)
        }        
    )
    // if1 icache hit, return hit and data
    // if1 icache miss, use itlb resp to construct mshr, if missqueue not full or refill req can merge, send refill req
    // tag read, data0 read, data1 read
    val if1_tagV_rdata = WireInit(VecInit(
        Seq.fill(p(L1CacheWayNum))(0.U((p(ICacheTagBits) + 1).W))
    ))
    val if1_rdata = WireInit(VecInit(
        Seq.fill(p(L1CacheWayNum))(0.U.asTypeOf(new ICacheLine))
    ))    
    val if1_predecode_rdata = WireInit(VecInit(
        Seq.fill(p(L1CacheWayNum))(0.U(metaInfoWidth.W))
    ))
    val if1_icache_req = PipeReg(io.if0_icache_req, io.if1_stall, io.if1_flush | io.if2_flush | io.commit_flush)
    val if1_offsets = WireInit(VecInit(
        Seq.tabulate(p(FETCH_WIDTH))(
            index => if1_icache_req.offset + index.U
        )
    ))

    for(i <- 0 until p(L1CacheWayNum)) {
        when(io.icache_refill_resp.valid && io.icacheReplaceRefillResp.way === i.U) {
            tagVMem(i).write(
                io.icache_refill_resp.pa.bits(
                    p(ICacheIndexBits) + p(L1CacheOffsetBits) + p(FETCH_OFFSET) - 1,
                    p(L1CacheOffsetBits) + p(FETCH_OFFSET)           
                ),
                ~(0.U),
                Cat(true.B, io.icache_refill_resp.pa.GetPPN)
            )
        }
        if1_tagV_rdata(i) := tagVMem(i).read(
            Mux(io.if0_icache_req.valid, io.if0_icache_req.index, 0.U)
        )

        when(io.icache_refill_resp.valid && io.icacheReplaceRefillResp.way === i.U) {
            dataMem(i).write(
                io.icache_refill_resp.pa.bits(
                    p(ICacheIndexBits) + p(L1CacheOffsetBits) + p(FETCH_OFFSET) - 1,
                    p(L1CacheOffsetBits) + p(FETCH_OFFSET)           
                ),
                ~(0.U),
                io.icache_refill_resp.data.asUInt
            )
            metaInfoMem(i).write(
                io.icache_refill_resp.pa.bits(
                    p(ICacheIndexBits) + p(L1CacheOffsetBits) + p(FETCH_OFFSET) - 1,
                    p(L1CacheOffsetBits) + p(FETCH_OFFSET)           
                ),
                ~(0.U),
                predecoder.io.icacheBranchMetaInfo.asUInt
            )
        }

        if1_rdata(i) := dataMem(i).read(
            Mux(io.if0_icache_req.valid, io.if0_icache_req.index, 0.U)
        ).asTypeOf(new ICacheLine)

        if1_predecode_rdata(i) := metaInfoMem(i).read(
            Mux(io.if0_icache_req.valid, io.if0_icache_req.index, 0.U)
        )
    }

    // itlb must hit
    val if1_icache_access_valid = 
        if1_icache_req.valid && 
        io.if1_itlb_resp(0).valid && 
        io.if1_itlb_resp(0).hit

    val if1_memReadResult = if1_rdata

    // if2 stage
    val if2_tagV_rdata = PipeReg(if1_tagV_rdata, io.if2_stall, io.if2_flush | io.commit_flush)
    val if2_itlb_resp = PipeReg(io.if1_itlb_resp, io.if2_stall, io.if2_flush | io.commit_flush)
    val if2_icache_req = PipeReg(if1_icache_req, io.if2_stall, io.if2_flush | io.commit_flush)

    val if2_hitArray = if2_tagV_rdata.map(
        tagV => tagV(p(ICacheTagBits) - 1) && tagV(p(ICacheTagBits) - 2, 0) === if2_itlb_resp(0).paddr.GetPPN
    )

    val if2_icache_access_valid = PipeReg(if1_icache_access_valid, io.if2_stall, io.if2_flush | io.commit_flush)

    io.if2_icache_hit := if2_hitArray.reduce(_||_)
    val if2_hitwayIndex = PriorityEncoder(if2_hitArray)
    // val memReadResult = VecInit((0 until p(L1CacheWayNum)).map(
    //     index => mem(index).read(io.if0_icache_req.index)
    // ))

    val if2_memReadResult = PipeReg(if1_memReadResult, io.if2_stall, io.if2_flush | io.commit_flush)
    val if2_predecode_rdata = PipeReg(if1_predecode_rdata, io.if2_stall, io.if2_flush | io.commit_flush)
    // mshr construct
    io.if2_icache_refill_req.valid := !io.if2_icache_hit && if2_icache_access_valid
    io.if2_icache_refill_req.pa := Cat(
        if2_itlb_resp(0).paddr.GetPPN, if2_icache_req.index, 0.U((p(L1CacheOffsetBits) + p(FETCH_OFFSET)).W)
    ).asTypeOf(new PhysicalAddress)

    val if2_offsets = PipeReg(if1_offsets, io.if2_stall, io.if2_flush | io.commit_flush)

    // need refill, mshr queue full and can not merge
    // need refill write
    io.if2_icache_stall_req := !io.if2_mshr_enqReady && io.if2_icache_refill_req.valid
    
    for(i <- 0 until io.if2_icacheline_resp.length) {
        io.if2_icacheline_resp(i) := if2_memReadResult(if2_hitwayIndex).datas(if2_offsets(i))
    }
    for(i <- 0 until io.if2_predecode.length) {
        io.if2_predecode(i) := if2_predecode_rdata(if2_hitwayIndex)(if2_offsets(i)).asTypeOf(new ICacheBranchMetaInfo)
    }

    io.icacheRefillUpdate.valid := io.icacheReplaceRefillResp.valid
    io.icacheRefillUpdate.way := io.icacheReplaceRefillResp.way
    io.icacheRefillUpdate.index := io.icache_refill_resp.pa.bits(
        p(ICacheIndexBits) + p(L1CacheOffsetBits) + p(FETCH_OFFSET) - 1,
        p(L1CacheOffsetBits) + p(FETCH_OFFSET)
    )

    io.icacheReplaceHitUpdate.valid := if2_hitArray.reduce(_||_)
    io.icacheReplaceHitUpdate.way := if2_hitwayIndex
    io.icacheReplaceHitUpdate.index := if2_icache_req.index
}