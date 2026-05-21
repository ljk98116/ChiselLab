package MyCPU.cache

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._

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
        val enqReady = Output(Bool())
        val deqItem = Output(new DCacheReq)        
    })

    val count = RegInit(0.U((ptrWidth + 1).W))

    val buffer = RegInit(VecInit(
        Seq.fill(bankNum)(0.U.asTypeOf(new DCacheReq))
    ))

    val reqs = VecInit((0 until bankNum).reverse.map(i => {
        if(i < p(LDU_NUM)) io.mem0_readReqs(i)
        else io.mem0_writeReqs(i - p(LDU_NUM))
    }))

    val bankConflictMask = VecInit((0 until bankNum).reverse.map(i => {
        reqs(i).valid && reqs(i).addr(
            p(L1CacheOffsetBits) + p(FETCH_OFFSET) + bankBits - 1,
            p(L1CacheOffsetBits) + p(FETCH_OFFSET)
        ) === id.U
    }))

    val prefixBankConflictNum = (0 until bankNum).reverse.map(i => {
        if(i == 0) 0.U
        else PopCount(bankConflictMask.asUInt(i - 1, 0))
    })

    val bankHitIdx = PriorityEncoder(bankConflictMask)
    val hasBankHit = bankConflictMask.asUInt.orR

    for(i <- 0 until bankNum) {
        when(bankConflictMask(i) && reqs(i).valid) {
            buffer(prefixBankConflictNum(i) - 1.U) := reqs(i)
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
// mem1 access cache
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
        // stall
        val mem0_bankconflict_stall = Output(Bool())
    })

    val bankAccessBuffers = Seq.tabulate(bankNum)(
        i => Module(new DCacheBankAccessBuffer(i))
    )

    val mem0_bank_reqs = WireInit(VecInit(
        Seq.fill(bankNum)(0.U.asTypeOf(new DCacheReq))
    ))

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

    
}