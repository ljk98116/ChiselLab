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
        val if2_icache_refill_req = Output(new ICacheRefillReq)
        val if2_mshr_enqReady = Input(Bool())
        // refill resp
        val icache_refill_resp = Input(new ICacheRefillResp)
        // to replace policy module req
        val icacheReplaceHitUpdate = Output(new ReplacePolicyHitUpdateInfo)
        // from replace policy module resp
        val icacheReplaceRefillResp = Input(new ReplacePolicyRefillRespInfo)
        // to replace policy module
        val icacheRefillUpdate = Output(new ReplacePolicyRefillUpdateInfo)
        // resp
        val if2_icacheline_resp = Output(Vec(p(L1CacheLineLength), UInt(p(DATA_WIDTH).W)))
        val if2_icache_hit = Output(Bool())
        val if2_predecode = Output(Vec(p(FETCH_WIDTH), new ICacheBranchMetaInfo))
        // stall signal
        val if2_icache_stall_req = Output(Bool())
    })

    val predecoder = Module(new PreDecode)
    predecoder.io.inst := io.icache_refill_resp.data

    // 256x34 if use nangate45
    val metaInfoWidth = (new ICacheBranchMetaInfo).getWidth * p(L1CacheLineLength)
    val singleMetaInfoWidth = (new ICacheBranchMetaInfo).getWidth

    val metaInfoMem = Seq.tabulate(p(L1CacheWayNum))(
        index => {
            if(p(UseNangate45)) Module(
                new Nangate45Bank(1 << p(L1CacheLineLength), UInt(34.W), s"ICacheBranchMetaInfoRam_${index}")
            )
            else Module(new LargeSyncBank(1 << p(L1CacheWayNum), Vec(p(L1CacheLineLength), new ICacheBranchMetaInfo)))
        }
    )

    val tagWidth = (new PhysicalAddress).getWidth
    val tagVMem = Seq.tabulate(p(L1CacheWayNum))(
        index => {
            // 256x34
            if(p(UseNangate45)) Module(new Nangate45Bank((1 << p(ICacheIndexBits)), UInt(tagWidth.W), s"ICacheTagVRam0_${index}"))
            else Module(new LargeSyncBank((1 << p(ICacheIndexBits)), UInt((p(ICacheTagBits) + 1).W)))
        }
    )

    val cacheLineWidth = p(L1CacheLineLength) * p(DATA_WIDTH)
    val dataMem0 = Seq.tabulate(p(L1CacheWayNum))(
        index => {
            // 256x2x4B => 256x96
            if(p(UseNangate45)) Module(new Nangate45Bank((1 << p(ICacheIndexBits)), UInt(96.W), s"ICacheDataRam0_${index}"))
            else Module(new LargeSyncBank((1 << p(ICacheIndexBits)), UInt((cacheLineWidth / 2).W)))
        }        
    )
    val dataMem1 = Seq.tabulate(p(L1CacheWayNum))(
        index => {
            // 256x2x4B => 256x96
            if(p(UseNangate45)) Module(new Nangate45Bank((1 << p(ICacheIndexBits)), UInt(96.W), s"ICacheDataRam1_${index}"))
            else Module(new LargeSyncBank((1 << p(ICacheIndexBits)), UInt((cacheLineWidth / 2).W)))
        }        
    )
    // if1 icache hit, return hit and data
    // if1 icache miss, use itlb resp to construct mshr, if missqueue not full or refill req can merge, send refill req
    // tag read, data0 read, data1 read
    val if1_tagV_rdata = WireInit(VecInit(
        Seq.fill(p(L1CacheWayNum))(0.U((p(ICacheTagBits) + 1).W))
    ))
    val if1_data0_rdata = WireInit(VecInit(
        Seq.fill(p(L1CacheWayNum))(0.U((cacheLineWidth / 2).W))
    ))
    val if1_data1_rdata = WireInit(VecInit(
        Seq.fill(p(L1CacheWayNum))(0.U((cacheLineWidth / 2).W))
    ))    
    val if1_predecode_rdata = WireInit(VecInit(
        Seq.fill(p(L1CacheWayNum))(0.U(metaInfoWidth.W))
    ))
    val if1_icache_req = PipeReg(io.if0_icache_req, io.if1_stall, io.if1_flush | io.if2_flush | io.commit_flush)

    for(i <- 0 until p(L1CacheWayNum)) {
        tagVMem(i).io.wen := io.icache_refill_resp.valid && io.icacheReplaceRefillResp.way === i.U
        tagVMem(i).io.waddr := io.icache_refill_resp.pa.bits(
            p(ICacheIndexBits) + p(L1CacheOffsetBits) + p(FETCH_OFFSET) - 1,
            p(L1CacheOffsetBits) + p(FETCH_OFFSET)           
        )
        tagVMem(i).io.wdata := Cat(true.B, io.icache_refill_resp.pa.GetPPN)
        tagVMem(i).io.raddr := Mux(io.if0_icache_req.valid, io.if0_icache_req.index, 0.U)
        if1_tagV_rdata(i) := tagVMem(i).io.rdata
        tagVMem(i).io.wmask := ~0.U

        dataMem0(i).io.wen := io.icache_refill_resp.valid && io.icacheReplaceRefillResp.way === i.U
        dataMem0(i).io.waddr := io.icache_refill_resp.pa.bits(
            p(ICacheIndexBits) + p(L1CacheOffsetBits) + p(FETCH_OFFSET) - 1,
            p(L1CacheOffsetBits) + p(FETCH_OFFSET)           
        )
        dataMem0(i).io.wmask := ~0.U
        if(p(UseNangate45)) {
            dataMem0(i).io.wdata := Cat(0.U(p(DATA_WIDTH).W),io.icache_refill_resp.data(1), io.icache_refill_resp.data(0))
        }
        else {
            dataMem0(i).io.wdata := Cat(io.icache_refill_resp.data(1), io.icache_refill_resp.data(0))
        }
        dataMem0(i).io.raddr := Mux(io.if0_icache_req.valid, io.if0_icache_req.index, 0.U)
        if1_data0_rdata(i) := dataMem0(i).io.rdata

        dataMem1(i).io.wen := io.icache_refill_resp.valid && io.icacheReplaceRefillResp.way === i.U
        dataMem1(i).io.waddr := io.icache_refill_resp.pa.bits(
            p(ICacheIndexBits) + p(L1CacheOffsetBits) + p(FETCH_OFFSET) - 1,
            p(L1CacheOffsetBits) + p(FETCH_OFFSET)           
        )
        if(p(UseNangate45)) {
            dataMem1(i).io.wdata := Cat(0.U(p(DATA_WIDTH).W), io.icache_refill_resp.data(3), io.icache_refill_resp.data(2))
        }
        else {
            dataMem1(i).io.wdata := Cat(io.icache_refill_resp.data(3), io.icache_refill_resp.data(2))
        }
        dataMem1(i).io.raddr := Mux(io.if0_icache_req.valid, io.if0_icache_req.index, 0.U)
        dataMem1(i).io.wmask := ~0.U
        if1_data1_rdata(i) := dataMem1(i).io.rdata

        metaInfoMem(i).io.wen := io.icache_refill_resp.valid && io.icacheReplaceRefillResp.way === i.U
        if(p(UseNangate45)) {
            metaInfoMem(i).io.wdata := Cat(0.U((34 - metaInfoWidth).W), predecoder.io.icacheBranchMetaInfo.asUInt)
        }
        else {
            metaInfoMem(i).io.wdata := predecoder.io.icacheBranchMetaInfo
        }
        metaInfoMem(i).io.wmask := (~0.U).asTypeOf(Vec(p(L1CacheLineLength), new ICacheBranchMetaInfo))
        metaInfoMem(i).io.waddr := io.icache_refill_resp.pa.bits(
            p(ICacheIndexBits) + p(L1CacheOffsetBits) + p(FETCH_OFFSET) - 1,
            p(L1CacheOffsetBits) + p(FETCH_OFFSET)           
        )
        metaInfoMem(i).io.raddr := Mux(io.if0_icache_req.valid, io.if0_icache_req.index, 0.U)
        if1_predecode_rdata(i) := metaInfoMem(i).io.rdata.asUInt
    }

    // itlb must hit
    val if1_icache_access_valid = 
        if1_icache_req.valid && 
        io.if1_itlb_resp(0).valid && 
        io.if1_itlb_resp(0).hit

    val if1_memReadResult = VecInit(if1_data0_rdata.zip(if1_data1_rdata).map({case (p0, p1) => Cat(p1(p(DATA_WIDTH) * 2 - 1, 0), p0(p(DATA_WIDTH) * 2 - 1, 0))}))

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

    // need refill, mshr queue full and can not merge
    // need refill write
    io.if2_icache_stall_req := (!io.if2_mshr_enqReady && io.if2_icache_refill_req.valid) || io.icache_refill_resp.valid
    for(i <- 0 until io.if2_icacheline_resp.length) {
        io.if2_icacheline_resp(i) := if2_memReadResult(if2_hitwayIndex)((i + 1) * p(DATA_WIDTH) - 1, i * p(DATA_WIDTH))
    }
    for(i <- 0 until io.if2_predecode.length) {
        if(p(UseNangate45)) {
            io.if2_predecode(i) := if2_predecode_rdata(if2_hitwayIndex)((i + 1) * singleMetaInfoWidth - 1, i * singleMetaInfoWidth)
        }
        else {
            io.if2_predecode(i) := if2_predecode_rdata(if2_hitwayIndex)(i).asTypeOf(new ICacheBranchMetaInfo)
        }
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