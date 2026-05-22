package MyCPU.cache

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._
import MyCPU.cache

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
        // mem1 stage, dtlb resps
        val mem1_ld_dtlb_resps = Input(Vec(p(LDU_NUM), new DTLBRespInfo))
        val mem1_st_dtlb_resps = Input(Vec(p(STU_NUM), new DTLBRespInfo))
        // mem2 stage, stage data, tagV at mem1, judge dcache hit, send refill req 
        val mem2_refill_reqs = Output(Vec(bankNum, new DCacheRefillReq))
        // send write infos to write buffer, use the changed the cacheline 
        val mem2_write_reqs = Output(Vec(p(STU_NUM), new DCacheWriteInfo))
        val mem2_dcache_mshr_enqReady = Input(Bool())
        // refill resp
        val dcache_refill_resp = Input(new DCacheRefillResp)
        // to replace policy module req
        val dcacheReplaceHitUpdate = Output(Vec(bankNum, new ReplacePolicyHitUpdateInfo))
        // from replace policy module resp
        val dcacheReplaceRefillResp = Input(Vec(bankNum, new ReplacePolicyRefillRespInfo))
        // to replace policy module
        val dcacheRefillUpdate = Output(Vec(bankNum, new ReplacePolicyRefillUpdateInfo))
        // have to stall all pipelines, because have full skidbuffer
        val mem0_bankconflict_stall = Output(Bool())
        // judge bank output, may need to stall a pipeline
        val mem0_st_dtlb_miss = Input(Vec(p(STU_NUM), Bool()))
        val mem0_ld_dtlb_miss = Input(Vec(p(LDU_NUM), Bool()))
        val mem0_st_stall = Output(Vec(p(STU_NUM), Bool()))
        val mem0_ld_stall = Output(Vec(p(LDU_NUM), Bool()))
        // mem2 stall, 
        val mem2_dcache_st_hit = Output(Vec(p(STU_NUM), Bool()))
        val mem2_dcache_ld_hit = Output(Vec(p(LDU_NUM), Bool()))
        val mem2_dcache_ld_data = Output(Vec(p(LDU_NUM), UInt(p(DATA_WIDTH).W)))
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

    // stage dtlb result and read result
    val mem2_ld_dtlb_resps = PipeReg(io.mem1_ld_dtlb_resps, io.stall_from_backpipe, io.flush)
    val mem2_st_dtlb_resps = PipeReg(io.mem1_st_dtlb_resps, io.stall_from_backpipe, io.flush)
    val mem2_tagVs = PipeReg(mem1_tagVs, io.stall_from_backpipe, io.flush)
    val mem2_datas = PipeReg(mem1_datas, io.stall_from_backpipe, io.flush)
    val mem2_bank_reqs = PipeReg(mem1_bank_reqs, io.stall_from_backpipe, io.flush)

    for(i <- 0 until bankNum) {
        bankAccessBuffers(i).io.flush := io.flush
        bankAccessBuffers(i).io.stall_from_backpipe := io.stall_from_backpipe 
        bankAccessBuffers(i).io.mem0_readReqs := io.mem0_readReqs
        bankAccessBuffers(i).io.mem0_writeReqs := io.mem0_writeReqs
        mem0_bank_reqs(i) := bankAccessBuffers(i).io.deqItem
    }

    io.mem0_bankconflict_stall := Cat((0 until bankNum).map(
        i => bankAccessBuffers(i).io.enqReady
    )).asUInt.andR

    for(i <- 0 until p(STU_NUM)) {
        io.mem0_st_stall(i) := false.B
        val st_stall_mask = WireInit(VecInit(
            Seq.fill(bankNum)(false.B)
        ))
        for(b <- 0 until bankNum) {
            st_stall_mask(b) := 
                bankAccessBuffers(b).io.deqItem.asUInt === io.mem0_writeReqs(i).asUInt // hit
        }
        io.mem0_st_stall(i) := !(st_stall_mask.asUInt.orR) | io.mem0_st_dtlb_miss(i)
    }

    for(i <- 0 until p(LDU_NUM)) {
        io.mem0_ld_stall(i) := false.B
        val ld_stall_mask = WireInit(VecInit(
            Seq.fill(bankNum)(false.B)
        ))
        for(b <- 0 until bankNum) {
            ld_stall_mask(b) := 
                bankAccessBuffers(b).io.deqItem.asUInt === io.mem0_readReqs(i).asUInt // hit
        }
        io.mem0_ld_stall(i) := !(ld_stall_mask.asUInt.orR) | io.mem0_ld_dtlb_miss(i)        
    }

    // mem1 get tagV and data for all banks
    // mem0 use io input
    // mem0 stall, if have dtlb miss
    for(w <- 0 until p(L1CacheWayNum)) {
        for(b <- 0 until bankNum) {
            tagVBanks(w)(b).io.raddr := bankAccessBuffers(b).io.deqItem.addr
            mem1_tagVs(w)(b) := tagVBanks(w)(b).io.rdata

            dataBanks(w)(b).io.raddr := bankAccessBuffers(b).io.deqItem.addr
            mem1_datas(w)(b) := dataBanks(w)(b).io.rdata
        }
    }

    // mem2 select bank output
    // mem2 judge dcache hit
    // mem2 write cachelines
    for(i <- 0 until p(STU_NUM)) {
        val bankHitMask = WireInit(VecInit(
            Seq.fill(bankNum)(false.B)
        ))

        for(b <- 0 until bankNum) {
            bankHitMask(b) := mem2_bank_reqs(b).valid && mem2_bank_reqs(b).pipeId === i.U
        }

        val hasBankHit = WireInit(false.B)
        hasBankHit := bankHitMask.asUInt.orR

        val bankHitIdx = WireInit(0.U(bankBits.W))
        bankHitIdx := PriorityEncoder(bankHitMask)

        val hitMask = WireInit(VecInit(
            Seq.fill(p(L1CacheWayNum))(false.B)
        ))

        val tagVs = Seq.fill(p(L1CacheWayNum))(
            WireInit(0.U((p(DCacheTagBits) + 1).W))
        )

        val datas = VecInit(Seq.fill(p(L1CacheWayNum))(
            WireInit(0.U(cacheLineWidth.W))
        ))

        for(w <- 0 until p(L1CacheWayNum)) {
            tagVs(w) := Mux(hasBankHit, mem2_tagVs(w)(bankHitIdx), 0.U)
            datas(w) := Mux(hasBankHit, mem2_datas(w)(bankHitIdx), 0.U)

            hitMask(w) := Mux(
                hasBankHit,
                tagVs(w)(p(DCacheTagBits) - 1) && 
                tagVs(w)(p(DCacheTagBits) - 2, 0) === mem2_st_dtlb_resps(i).paddr.GetPPN &&
                mem2_st_dtlb_resps(i).hit,
                false.B
            )
        }

        val dcache_hit = WireInit(false.B)
        val dcache_hitWay = WireInit(0.U(log2Ceil(p(L1CacheWayNum)).W))

        dcache_hit := hitMask.asUInt.orR
        dcache_hitWay := PriorityEncoder(hitMask)

        io.mem2_dcache_st_hit(i) := dcache_hit

        // mem3 update cacheline data and write to write through buffer
        val mem3_dcache_hit = PipeReg(dcache_hit, io.stall_from_backpipe, io.flush)
        val mem3_dcache_hitWay = PipeReg(dcache_hitWay, io.stall_from_backpipe, io.flush)
        val mem3_datas = PipeReg(datas, io.stall_from_backpipe, io.flush)
        val mem3_req_mask = PipeReg(VecInit(mem2_bank_reqs.map(_.mask)), io.stall_from_backpipe, io.flush)
        val mem3_bankHitIdx = PipeReg(bankHitIdx, io.stall_from_backpipe, io.flush)
        val mem3_hasBankHit = PipeReg(hasBankHit, io.stall_from_backpipe, io.flush)

        val mem3_dcache_cacheline = WireInit(0.U(cacheLineWidth.W))
        mem3_dcache_cacheline := Mux(mem3_dcache_hit, mem3_datas(mem3_dcache_hitWay), 0.U)

        val updateData = WireInit(0.U(p(DATA_WIDTH).W))
        updateData := Mux(
            mem3_hasBankHit && mem3_dcache_hit, 
            mem3_datas(mem3_dcache_hitWay) | mem3_req_mask(mem3_bankHitIdx).mask,
            0.U
        )

        
    }

}