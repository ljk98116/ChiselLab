package MyCPU.cache

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._
import MyCPU.cache._

class DCacheBankAccessBuffer(id: Int)(implicit p: Parameters) extends Module {
    // sigle way sram ports
    val readPortNum = p(LDU_NUM)
    val writePortNum = p(STU_NUM)
    val bankNum = writePortNum + readPortNum
    // use writePortNum as bankNum
    // copy readPortNum within a bank
    val bankBits = log2Ceil(bankNum)
    val ptrWidth = log2Ceil(bankBits)

    val io = IO(new Bundle {
        val flush = Input(Bool())
        val stall_from_backpipe = Input(Bool())
        // mem0 stage, pre read cacheline and tagV(both need read cacheline)
        val mem0_readReqs = Input(Vec(p(LDU_NUM), new DCacheReq))
        val mem0_writeReqs = Input(Vec(p(STU_NUM), new DCacheReq))
        val mem0_st_stall = Input(Vec(p(STU_NUM), Bool()))
        val mem0_ld_stall = Input(Vec(p(LDU_NUM), Bool()))
        val enqReady = Output(Bool())
        val deqItem = Output(new DCacheReq)    
    })

    val count = RegInit(0.U((ptrWidth + 1).W))

    val buffer = RegInit(VecInit(
        Seq.fill(bankNum)(0.U.asTypeOf(new DCacheReq))
    ))

    val reqs = VecInit((0 until bankNum).reverse.map(i => {
        if(i < p(STU_NUM)) io.mem0_writeReqs(i)
        else io.mem0_readReqs(i - p(STU_NUM))
    }))

    val bankConflictMask = VecInit((0 until bankNum).reverse.map(i => {
        if(i < p(STU_NUM)) {
            reqs(i).valid && reqs(i).addr(
                p(L1CacheOffsetBits) + p(FETCH_OFFSET) + bankBits - 1,
                p(L1CacheOffsetBits) + p(FETCH_OFFSET)
            ) === id.U && io.mem0_st_stall(i)
        }
        else {
            reqs(i).valid && reqs(i).addr(
                p(L1CacheOffsetBits) + p(FETCH_OFFSET) + bankBits - 1,
                p(L1CacheOffsetBits) + p(FETCH_OFFSET)
            ) === id.U && io.mem0_ld_stall(i)
        }
    }))

    val prefixBankConflictNum = (0 until bankNum).reverse.map(i => {
        if(i == 0) 0.U
        else PopCount(bankConflictMask.asUInt(i - 1, 0))
    })

    val bankHitIdx = PriorityEncoder(bankConflictMask)
    val hasBankHit = bankConflictMask.asUInt.orR && !io.stall_from_backpipe

    for(i <- 0 until bankNum) {
        when(bankConflictMask(i) && reqs(i).valid && !io.stall_from_backpipe) {
            buffer(count + prefixBankConflictNum(i) - 1.U) := reqs(i)
        }
    }

    count := Mux(count =/= 0.U, count - 1.U, count + prefixBankConflictNum(bankNum - 1) - 1.U)
    io.enqReady := count + prefixBankConflictNum(bankNum - 1) < (bankNum - 1).U 

    io.deqItem := Mux(
        count =/= 0.U,
        buffer(count),
        Mux(hasBankHit, reqs(bankHitIdx), 0.U.asTypeOf(new DCacheReq))
    )
}

// need to designed as multiport
// write through design, use L2 cache as write back
// mem0 judge dtlb hit, if miss, stall mem0
// mem1 access cache, if mem0 stall, not enq to skidbuffer
class DCacheSram(implicit p: Parameters) extends Module {
    // sigle way sram ports
    val readPortNum = p(LDU_NUM)
    val writePortNum = p(STU_NUM)
    val bankNum = writePortNum + readPortNum
    // use writePortNum as bankNum
    // copy readPortNum within a bank
    val bankBits = log2Ceil(bankNum)
    // read/write not hit, make refill read req
    // when write hit, put the written line to write buffer and write to L2 cache or dram
    // make a write req bank buffer to store the write req to dcache
    val io = IO(new Bundle {
        val flush = Input(Bool())
        val stall_from_backpipe = Input(Bool())
        // mem0 stage, pre read cacheline and tagV(both need read cacheline)
        val mem0_readReqs = Input(Vec(p(LDU_NUM), new DCacheReq))
        val mem0_writeReqs = Input(Vec(p(STU_NUM), new DCacheReq))
        // mem2 stage, dtlb resps
        val mem2_dtlb_resps = Input(Vec(bankNum, new DTLBRespInfo))
        // mem3 stage, stage data, tagV at mem1, judge dcache hit, send refill req 
        // dcache mshr io
        val mem3_refill_reqs = Output(Vec(bankNum, new DCacheRefillReq))
        val mem3_dcache_mshr_enqReady = Input(Bool())
        // send write infos to write buffer, use the changed the cacheline 
        val mem2_write_reqs = Output(Vec(p(STU_NUM), new DCacheWriteInfo))
        // refill resp, broadcast to load waiting queue to get data
        val dcacheRefillResp = Input(new DCacheRefillResp)
        // to replace policy module req
        val dcacheReplaceHitUpdate = Output(Vec(bankNum, new ReplacePolicyHitUpdateInfo))
        // from replace policy module resp
        val dcacheReplaceRefillResp = Input(new ReplacePolicyRefillRespInfo)
        // to replace policy module
        val dcacheRefillUpdate = Output(new ReplacePolicyRefillUpdateInfo)
        // have to stall all pipelines, because have full skidbuffer
        val mem0_bankconflict_stall = Output(Bool())
        // judge bank output, may need to stall a pipeline
        // use bank output to access dtlb at mem1
        val mem1_dtlb_reqs = Output(Vec(bankNum, new DTLBReqInfo))
        // tlb miss, stall
        val mem2_dtlb_miss_stall = Output(Bool())
        // stall when mshr queue can not enq
        val mem3_mshr_stall = Output(Bool())
        // write back to dram
        val mem3_dcache_wb_buffer_enqReady = Input(Bool()) // may generate stall
        val mem3_dcache_wb_req = Output(Vec(bankNum, new DCacheWriteBackReq))
        val mem3_dcache_wb_stall = Output(Bool())
        // mem4 change banked data to pipelines
        val mem4_dcache_ld_valid = Output(Vec(p(LDU_NUM), Bool()))
        val mem4_dcache_ld_data = Output(Vec(p(LDU_NUM), UInt(p(DATA_WIDTH).W)))
    })

    val bankAccessBuffers = Seq.tabulate(bankNum)(
        i => Module(new DCacheBankAccessBuffer(i))
    )

    val tagWidth = (new PhysicalAddress).getWidth
    val cacheLineWidth = p(L1CacheLineLength) * p(DATA_WIDTH)
    val depth = 1 << p(DCacheIndexBits)
    val perBankSize = depth / bankNum

    val tagVBanks = Seq.fill(p(L1CacheWayNum))(
        Seq.fill(bankNum)(
            Module(new LargeSyncBank(perBankSize, UInt((p(DCacheTagBits) + 1).W)))
        )
    )

    val dataBanks = Seq.fill(p(L1CacheWayNum))(
        Seq.fill(bankNum)(
            Module(new LargeSyncBank(perBankSize, UInt(cacheLineWidth.W)))
        )
    )

    val mem0_bank_reqs = WireInit(VecInit(
        Seq.fill(bankNum)(0.U.asTypeOf(new DCacheReq))
    ))

    val mem1_tagVs = WireInit(VecInit(
        Seq.fill(p(L1CacheWayNum))(
            VecInit(
                Seq.fill(bankNum)(0.U((p(DCacheTagBits) + 1).W))
            )
        )
    ))

    val mem1_datas = WireInit(VecInit(
        Seq.fill(p(L1CacheWayNum))(
            VecInit(
                Seq.fill(bankNum)(0.U((cacheLineWidth).W))
            )
        )
    ))

    val mem1_bank_reqs = PipeReg(mem0_bank_reqs, io.stall_from_backpipe, io.flush)

    val mem2_bankHits = WireInit(VecInit(
        Seq.fill(bankNum)(false.B)
    ))

    val mem2_bankHitWays = WireInit(VecInit(
        Seq.fill(bankNum)(0.U(log2Ceil(p(L1CacheWayNum)).W))
    ))

    val mem2_tagVs = PipeReg(
        mem1_tagVs, 
        io.stall_from_backpipe || io.mem3_mshr_stall || 
        io.mem2_dtlb_miss_stall || !io.mem3_dcache_wb_buffer_enqReady, 
        io.flush
    )
    val mem2_datas = PipeReg(
        mem1_datas, 
        io.stall_from_backpipe || io.mem3_mshr_stall || 
        io.mem2_dtlb_miss_stall || !io.mem3_dcache_wb_buffer_enqReady,  
        io.flush
    )

    val mem2_bank_reqs = PipeReg(
        mem1_bank_reqs, 
        io.stall_from_backpipe || io.mem3_mshr_stall || 
        io.mem2_dtlb_miss_stall || !io.mem3_dcache_wb_buffer_enqReady,  
        io.flush
    )
    val mem2_dtlb_reqs = PipeReg(
        io.mem1_dtlb_reqs,
        io.stall_from_backpipe || io.mem3_mshr_stall || 
        io.mem2_dtlb_miss_stall || !io.mem3_dcache_wb_buffer_enqReady,  
        io.flush        
    )
    val mem3_datas = PipeReg(
        mem2_datas, 
        io.stall_from_backpipe || !io.mem3_dcache_wb_buffer_enqReady ||
        io.mem3_mshr_stall, 
        io.flush
    )
    val mem3_bankHits = PipeReg(
        mem2_bankHits, 
        io.stall_from_backpipe || !io.mem3_dcache_wb_buffer_enqReady ||
        io.mem3_mshr_stall, 
        io.flush
    )
    val mem3_bankHitWays = PipeReg(
        mem2_bankHitWays, 
        io.stall_from_backpipe || !io.mem3_dcache_wb_buffer_enqReady ||
        io.mem3_mshr_stall, 
        io.flush
    )
    val mem3_bank_reqs = PipeReg(
        mem2_bank_reqs, 
        io.stall_from_backpipe || !io.mem3_dcache_wb_buffer_enqReady ||
        io.mem3_mshr_stall, 
        io.flush
    ) // used for judge ld/st
    val mem3_bank_pas = VecInit(io.mem3_refill_reqs.map(_.pa))
    val mem3_dtlb_resps = PipeReg(
        io.mem2_dtlb_resps, 
        io.stall_from_backpipe || !io.mem3_dcache_wb_buffer_enqReady ||
        io.mem3_mshr_stall, 
        io.flush
    )
    val mem3_dtlb_reqs = PipeReg(
        mem2_dtlb_reqs, 
        io.stall_from_backpipe || !io.mem3_dcache_wb_buffer_enqReady ||
        io.mem3_mshr_stall, 
        io.flush        
    )

    val mem3_bankReadData = WireInit(VecInit(
        Seq.fill(bankNum)(0.U(p(DATA_WIDTH).W))
    ))

    val mem3_bankReadValid = WireInit(VecInit(
        Seq.fill(bankNum)(false.B)
    ))

    val mem3_bankReadMask = WireInit(VecInit(
        Seq.fill(bankNum)(0.U(p(DATA_WIDTH).W))
    ))

    val mem4_bankReadData = PipeReg(mem3_bankReadData, io.stall_from_backpipe, io.flush)
    val mem4_bankReadValid = PipeReg(mem3_bankReadValid, io.stall_from_backpipe, io.flush)
    val mem4_bankReadMask = PipeReg(mem3_bankReadMask, io.stall_from_backpipe, io.flush)
    val mem4_bank_reqs = PipeReg(mem3_bank_reqs, io.stall_from_backpipe, io.flush)

    for(i <- 0 until bankNum) {
        bankAccessBuffers(i).io.flush := io.flush
        bankAccessBuffers(i).io.stall_from_backpipe := 
            io.stall_from_backpipe || io.mem3_mshr_stall || 
            io.mem2_dtlb_miss_stall || !io.mem3_dcache_wb_buffer_enqReady
        bankAccessBuffers(i).io.mem0_readReqs := io.mem0_readReqs
        bankAccessBuffers(i).io.mem0_writeReqs := io.mem0_writeReqs
        mem0_bank_reqs(i) := bankAccessBuffers(i).io.deqItem
    }

    io.mem0_bankconflict_stall := Cat((0 until bankNum).map(
        i => bankAccessBuffers(i).io.enqReady
    )).asUInt.andR

    for(b <- 0 until bankNum) {
        io.mem1_dtlb_reqs(b).valid := mem1_bank_reqs(b).valid
        io.mem1_dtlb_reqs(b).addr := mem1_bank_reqs(b).addr
    }

    // mem1 get tagV and data for all banks
    // mem0 use io input
    // mem0 stall, if have dtlb miss
    for(w <- 0 until p(L1CacheWayNum)) {
        for(b <- 0 until bankNum) {
            val idx = bankAccessBuffers(b).io.deqItem.addr(
                p(DCacheIndexBits) + p(L1CacheOffsetBits) + p(FETCH_OFFSET) - 1,  
                p(L1CacheOffsetBits) + p(FETCH_OFFSET)
            )
            tagVBanks(w)(b).io.raddr := idx
            mem1_tagVs(w)(b) := tagVBanks(w)(b).io.rdata

            dataBanks(w)(b).io.raddr := idx
            mem1_datas(w)(b) := dataBanks(w)(b).io.rdata
        }
    }
 
    // mem2 judge dcache hit, get data to write
    // mem3 write cachelines, select bank output
    // when mshr can not enq, stall all pipelines

    for(b <- 0 until bankNum) {
        val hitMask = WireInit(VecInit(
            Seq.fill(p(L1CacheWayNum))(false.B)
        ))
        for(w <- 0 until p(L1CacheWayNum)) {
            hitMask(w) := mem2_tagVs(w)(b)(0) && 
                mem2_tagVs(w)(b)(p(DCacheTagBits) - 1, 1) === io.mem2_dtlb_resps(b).paddr.GetPPN &&
                io.mem2_dtlb_resps(b).hit && 
                mem2_bank_reqs(b).valid
        }
        mem2_bankHits(b) := hitMask.asUInt.orR
        mem2_bankHitWays(b) := PriorityEncoder(hitMask)

        // hit update replace policy at mem3
        io.dcacheReplaceHitUpdate(b).valid := mem3_bankHits(b)
        io.dcacheReplaceHitUpdate(b).index := mem3_bank_reqs(b).addr(
            p(DCacheIndexBits) + p(L1CacheOffsetBits) + p(FETCH_OFFSET) - 1,  
            p(L1CacheOffsetBits) + p(FETCH_OFFSET)
        )
        io.dcacheReplaceHitUpdate(b).way := mem3_bankHitWays(b)

        io.mem3_refill_reqs(b).valid := mem3_bank_reqs(b).valid
        io.mem3_refill_reqs(b).pa.bits := Cat(
            mem3_dtlb_resps(b).paddr.GetPPN, 
            mem3_dtlb_reqs(b).addr(
                p(DCacheIndexBits) + p(L1CacheOffsetBits) + p(FETCH_OFFSET) - 1, 
                p(L1CacheOffsetBits) + p(FETCH_OFFSET)
            ), 0.U((p(L1CacheOffsetBits) + p(FETCH_OFFSET)).W)
        ).asTypeOf(new PhysicalAddress)
    }

    io.mem2_dtlb_miss_stall := !io.mem2_dtlb_resps.map(_.hit).reduce(_&_)
    io.mem3_mshr_stall := !io.mem3_dcache_mshr_enqReady

    for(b <- 0 until bankNum) {
        io.mem4_dcache_ld_valid(b) := false.B
        io.mem4_dcache_ld_data(b) := 0.U
    }

    for(b <- 0 until bankNum) {
        val mem3_ways = VecInit((0 until p(L1CacheWayNum)).map(i => mem3_datas(i)(b))) // choose bank b data for all ways
        val mem3_bankHit = mem3_bankHits(b)
        val mem3_bankHitWay = mem3_bankHitWays(b)

        val mem3_isStore = WireInit(false.B)
        mem3_isStore := mem3_bank_reqs(b).wen && mem3_bank_reqs(b).valid

        val mem3_isLoad = WireInit(false.B)
        mem3_isLoad := mem3_bank_reqs(b).valid && !mem3_bank_reqs(b).wen

        val mem3_lineIdx = mem3_bank_reqs(b).addr(p(L1CacheOffsetBits) + p(FETCH_OFFSET) - 1, p(FETCH_OFFSET))

        // select hit way data
        val cacheLine = WireInit(0.U.asTypeOf(new DCacheLine))
        cacheLine := Mux(mem3_bankHit, mem3_ways(mem3_bankHitWay).asTypeOf(new DCacheLine), 0.U.asTypeOf(new DCacheLine))
    
        // select line data
        when(mem3_isLoad) {
            mem3_bankReadData(b) := cacheLine.datas(mem3_lineIdx) & mem3_bank_reqs(b).mask
            mem3_bankReadValid(b) := mem3_bankHit
            mem3_bankReadMask(b) := mem3_bank_reqs(b).mask
        }.otherwise {
            mem3_bankReadData(b) := 0.U
            mem3_bankReadValid(b) := false.B
            mem3_bankReadMask(b) := 0.U
        }

        // mem4 return to pipeline
        val pipeId = WireInit(0.U(bankBits.W))
        val pipeIsStore = WireInit(false.B)
        val pipeIsLoad = WireInit(false.B)
        val signExtData = WireInit(0.U(p(DATA_WIDTH).W))

        signExtData := mem4_bankReadData(b)
        switch(mem4_bankReadMask(b)) {
            is("h000000FF".U) {
                signExtData := Cat(Fill(24, mem4_bankReadData(b)(7)), mem4_bankReadData(b))
            }
            is("h0000FFFF".U) {
                signExtData := Cat(Fill(16, mem4_bankReadData(b)(15)), mem4_bankReadData(b))
            }
        }

        pipeId := mem4_bank_reqs(b).pipeId
        pipeIsStore := mem4_bank_reqs(b).wen && mem4_bank_reqs(b).valid
        pipeIsLoad := !mem4_bank_reqs(b).wen && mem4_bank_reqs(b).valid

        when(pipeIsLoad) {
            io.mem4_dcache_ld_valid(pipeId) := mem4_bankReadValid(b)
            io.mem4_dcache_ld_data(pipeId) := Mux(
                mem4_bank_reqs(b).isSignExt,
                signExtData,
                mem4_bankReadData(b)
            )
        }

        // select line data to write
        val cacheLineResult = WireInit(0.U.asTypeOf(new DCacheLine))
        cacheLineResult := cacheLine
        cacheLineResult.datas(mem3_lineIdx) := (cacheLine.datas(mem3_lineIdx) & ~mem3_bank_reqs(b).mask) |
            (cacheLine.datas(mem3_lineIdx) & mem3_bank_reqs(b).mask)

        // write to dcache
        for(w <- 0 until p(L1CacheWayNum)) {
            dataBanks(w)(b).io.wen := mem3_bankHit && mem3_isStore
            dataBanks(w)(b).io.waddr := mem3_bank_reqs(b).addr(
                p(DCacheIndexBits) - 1 + p(L1CacheOffsetBits) + p(FETCH_OFFSET),
                bankBits + p(L1CacheOffsetBits) + p(FETCH_OFFSET)
            )
            dataBanks(w)(b).io.wdata := mem3_bank_reqs(b).data
            dataBanks(w)(b).io.wmask := ~((0.U)(cacheLineWidth.W))
        }

        // write the cacheLineResult to writeback buffer
        io.mem3_dcache_wb_req(b).valid := mem3_isStore
        io.mem3_dcache_wb_req(b).cacheLine := cacheLineResult
        io.mem3_dcache_wb_req(b).pa := mem3_bank_pas(b)

        // do refill
        
    }

    io.mem3_dcache_wb_stall := !io.mem3_dcache_wb_buffer_enqReady
}