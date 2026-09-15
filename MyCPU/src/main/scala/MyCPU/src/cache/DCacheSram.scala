package MyCPU.cache

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._
import MyCPU.cache._
import dataclass.data

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
    val tagWidth = (new PhysicalAddress).getWidth
    val cacheLineWidth = p(L1CacheLineLength) * p(DATA_WIDTH)
    val depth = 1 << p(DCacheIndexBits)
    val perBankSize = depth / bankNum
    // read/write not hit, make refill read req
    // when write hit, put the written line to write buffer and write to L2 cache or dram
    // make a write req bank buffer to store the write req to dcache
    val io = IO(new Bundle {
        val flush = Input(Bool())
        // mem0 stage, pre read cacheline and tagV(both need read cacheline)
        val mem0_bank_reqs = Input(Vec(bankNum, new DCacheReq))
        // mem2 stage, dtlb resps, all insts update their pas
        // mem3 all insts enq for the 
        val mem2_dtlb_resps = Input(Vec(bankNum, new DTLBRespInfo))
        // mem3 stage, stage data, tagV at mem1, judge dcache hit, send refill req 
        // dcache mshr io
        val mem3_refill_reqs = Output(Vec(bankNum, new CacheRefillReq))
        val mem3_dcache_mshr_enqReady = Input(Bool())
        // refill resp, broadcast to load waiting queue to get data
        val dcacheRefillResp = Input(new DCacheRefillResp)
        // to replace policy module req
        val dcacheReplaceHitUpdate = Output(Vec(bankNum, new ReplacePolicyHitUpdateInfo))
        // from replace policy module resp
        val dcacheReplaceRefillResp = Input(new ReplacePolicyRefillRespInfo)
        // to replace policy module
        val dcacheRefillUpdate = Output(new DCacheReplacePolicyRefillUpdateInfo)
        // judge bank output, may need to stall a pipeline
        // use bank output to access dtlb at mem1
        val mem1_dtlb_reqs = Output(Vec(bankNum, new DTLBReqInfo))
        // tlb miss, stall
        val mem2_dtlb_miss_infos = Output(Vec(bankNum, new DTLBMissInfo))
        // stall when mshr queue can not enq
        val mem3_mshr_stall = Output(Bool())
        val mem3_dcache_hitInfos = Output(Vec(bankNum, new DCacheHitInfo))
        // mem4 write back to dcache, from dcache wb queue
        val mem4_ldu_stall = Input(Bool())
        val mem4_stu_stall = Input(Bool())
        val mem4_wb_enq = Input(new DCacheWriteBackReq)
    })

    val mem1_stalls = WireInit(VecInit(
        Seq.fill(bankNum)(false.B)
    ))

    val mem2_stalls = WireInit(VecInit(
        Seq.fill(bankNum)(false.B)
    ))

    val mem3_stalls = WireInit(VecInit(
        Seq.fill(bankNum)(false.B)
    ))

    val tagVBanks = Seq.tabulate(p(L1CacheWayNum))(
        w => Seq.tabulate(bankNum)( 
            b => new LargeSyncBank(clock, reset, perBankSize, UInt((p(DCacheTagBits) + 1).W), s"dacheTagVBank[$w][$b]")
        )
    )

    val dataBanks = Seq.tabulate(p(L1CacheWayNum))(
        w => Seq.tabulate(bankNum)(
            b => new LargeSyncBank(clock, reset, perBankSize, UInt(cacheLineWidth.W), s"dcacheDataBank[$w][$b]")
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

    val mem1_bank_reqs = VecInit((0 until bankNum).map(
        i => PipeReg(mem0_bank_reqs(i), mem1_stalls(i), io.flush)
    ))

    val mem2_bankHits = WireInit(VecInit(
        Seq.fill(bankNum)(false.B)
    ))

    val mem2_bankHitWays = WireInit(VecInit(
        Seq.fill(bankNum)(0.U(log2Ceil(p(L1CacheWayNum)).W))
    ))

    val mem2_tagVs = VecInit((0 until bankNum).map(
        i => PipeReg(mem1_tagVs(i), mem2_stalls(i), io.flush)
    ))

    val mem2_datas = VecInit((0 until bankNum).map(
        i => PipeReg(mem1_datas(i), mem2_stalls(i), io.flush)
    ))

    val mem2_bank_reqs = VecInit((0 until bankNum).map(
        i => PipeReg(mem1_bank_reqs(i), mem2_stalls(i), io.flush)
    ))

    val mem2_dtlb_reqs = VecInit((0 until bankNum).map(
        i => PipeReg(io.mem1_dtlb_reqs(i), mem2_stalls(i), io.flush)
    ))

    val mem3_datas = VecInit((0 until bankNum).map(
        i => PipeReg(mem2_datas(i), mem3_stalls(i), io.flush)
    ))

    val mem3_bankHits = VecInit((0 until bankNum).map(
        i => PipeReg(mem2_bankHits(i), mem3_stalls(i), io.flush)
    ))

    val mem3_bankHitWays = VecInit((0 until bankNum).map(
        i => PipeReg(mem2_bankHitWays(i), mem3_stalls(i), io.flush)
    ))

    val mem3_bank_reqs = VecInit((0 until bankNum).map(
        i => PipeReg(mem2_bank_reqs(i), mem3_stalls(i), io.flush)
    )) 
    // used for judge ld/st
    val mem3_bank_pas = VecInit(io.mem3_refill_reqs.map(_.pa))
    val mem3_dtlb_resps = VecInit((0 until bankNum).map(
        i => PipeReg(io.mem2_dtlb_resps(i), mem3_stalls(i), io.flush)
    ))
    val mem3_dtlb_reqs = VecInit((0 until bankNum).map(
        i => PipeReg(mem2_dtlb_reqs(i), mem3_stalls(i), io.flush)
    ))

    val mem3_cacheLines = WireInit(VecInit(
        Seq.fill(bankNum)(0.U(cacheLineWidth.W))
    ))

    for(b <- 0 until bankNum) {
        io.mem1_dtlb_reqs(b).valid := mem1_bank_reqs(b).valid
        io.mem1_dtlb_reqs(b).addr := mem1_bank_reqs(b).addr
        io.mem1_dtlb_reqs(b).wen := mem1_bank_reqs(b).wen
    }

    // mem1 get tagV and data for all banks
    // mem0 use io input
    // mem0 stall, if have dtlb miss
    for(w <- 0 until p(L1CacheWayNum)) {
        for(b <- 0 until bankNum) {
            val idx = io.mem0_bank_reqs(b).addr(
                p(DCacheIndexBits) + p(L1CacheOffsetBits) + p(FETCH_OFFSET) - 1,  
                p(L1CacheOffsetBits) + p(FETCH_OFFSET)
            )
            // tagVBanks(w)(b).io.raddr := idx
            // mem1_tagVs(w)(b) := tagVBanks(w)(b).io.rdata
            mem1_tagVs(w)(b) := tagVBanks(w)(b).read(idx)

            // dataBanks(w)(b).io.raddr := idx
            // mem1_datas(w)(b) := dataBanks(w)(b).io.rdata
            mem1_datas(w)(b) := dataBanks(w)(b).read(idx)
        }
    }
 
    // mem2 judge dcache hit, get data to write
    // mem3 select bank output
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

        io.mem2_dtlb_miss_infos(b).valid := !io.mem2_dtlb_resps(b).hit && io.mem2_dtlb_resps(b).valid
        io.mem2_dtlb_miss_infos(b).robLoc := mem2_bank_reqs(b).robLoc

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
            ), 
            0.U((p(L1CacheOffsetBits) + p(FETCH_OFFSET)).W)
        )

        mem3_cacheLines(b) := mem3_datas(mem3_bankHitWays(b))(b)

        io.mem3_dcache_hitInfos(b).valid := mem3_bank_reqs(b).valid
        io.mem3_dcache_hitInfos(b).hit := mem3_bankHits(b)
        io.mem3_dcache_hitInfos(b).hitWay := mem3_bankHitWays(b)
        io.mem3_dcache_hitInfos(b).data := mem3_cacheLines(b).asTypeOf(new DCacheLine)
        io.mem3_dcache_hitInfos(b).pa := mem3_bank_pas(b)
        io.mem3_dcache_hitInfos(b).mask := mem3_bank_reqs(b).mask
        io.mem3_dcache_hitInfos(b).wdata := mem3_bank_reqs(b).data
        io.mem3_dcache_hitInfos(b).robLoc := mem3_bank_reqs(b).robLoc

        // refill
        val refillBankId = io.dcacheRefillResp.pa.bits(
            p(L1CacheOffsetBits) + p(FETCH_OFFSET) + bankBits - 1, 
            p(L1CacheOffsetBits) + p(FETCH_OFFSET)
        )
        val refillBankRow = io.dcacheRefillResp.pa.bits(
            p(DCacheIndexBits) + p(L1CacheOffsetBits) + p(FETCH_OFFSET) - 1, 
            p(L1CacheOffsetBits) + p(FETCH_OFFSET) + bankBits
        )
        val refillBankWay = io.dcacheReplaceRefillResp.way
        val refillValid = io.dcacheRefillResp.valid && refillBankId === b.U && io.dcacheReplaceRefillResp.valid
        for(w <- 0 until p(L1CacheWayNum)) {
            // tagVBanks(w)(b).io.wen := false.B
            // tagVBanks(w)(b).io.waddr := 0.U
            // tagVBanks(w)(b).io.wdata := 0.U
            // tagVBanks(w)(b).io.wmask := ~(0.U)

            // dataBanks(w)(b).io.wen := false.B
            // dataBanks(w)(b).io.waddr := 0.U
            // dataBanks(w)(b).io.wdata := 0.U
            // dataBanks(w)(b).io.wmask := ~(0.U)

            when(w.U === refillBankWay && refillValid) {
                // tagVBanks(w)(b).io.wen := true.B
                // tagVBanks(w)(b).io.waddr := refillBankRow
                // tagVBanks(w)(b).io.wdata := Cat(
                //     true.B, io.dcacheRefillResp.pa.GetPPN)
                tagVBanks(w)(b).write(refillBankRow, ~(0.U), 
                    Cat(true.B, io.dcacheRefillResp.pa.GetPPN))

                // dataBanks(w)(b).io.wen := true.B
                // dataBanks(w)(b).io.waddr := refillBankRow
                // dataBanks(w)(b).io.wdata := io.dcacheRefillResp.data.asUInt
                dataBanks(w)(b).write(refillBankRow, ~(0.U)(io.dcacheRefillResp.data.getWidth.W),
                    io.dcacheRefillResp.data.asUInt
                )
            }
        }

        val wbValid = io.mem4_wb_enq.valid
        val wbBankId = io.mem4_wb_enq.bankId
        val wbHitWay = io.mem4_wb_enq.hitWay
        val wbBankRow = io.mem4_wb_enq.pa.bits(
            p(FETCH_OFFSET) + p(L1CacheOffsetBits) + p(DCacheIndexBits) - 1,
            p(FETCH_OFFSET) + p(L1CacheOffsetBits) + bankBits
        )
        val wbTag = io.mem4_wb_enq.pa.GetPPN
        for(w <- 0 until p(L1CacheWayNum)) {
            when(wbValid && wbBankId === b.U && wbHitWay === w.U) {
                // tagVBanks(w)(b).io.wen := true.B
                // tagVBanks(w)(b).io.waddr := wbBankRow
                // tagVBanks(w)(b).io.wdata := Cat(true.B, wbTag)
                tagVBanks(w)(b).write(wbBankRow, ~(0.U), Cat(true.B, wbTag))

                // dataBanks(w)(b).io.wen := true.B
                // dataBanks(w)(b).io.waddr := wbBankRow
                // dataBanks(w)(b).io.wdata := io.mem4_wb_enq.cacheLine.datas.asUInt
                dataBanks(w)(b).write(wbBankRow, ~(0.U), io.mem4_wb_enq.cacheLine.datas.asUInt)
            }
        }

        // stall signals, need to opt
        mem1_stalls(b) := io.mem3_mshr_stall || io.mem2_dtlb_miss_infos(b).valid || io.mem4_ldu_stall || io.mem4_stu_stall
        mem2_stalls(b) := io.mem3_mshr_stall || io.mem2_dtlb_miss_infos(b).valid || io.mem4_ldu_stall || io.mem4_stu_stall
        mem3_stalls(b) := io.mem3_mshr_stall || io.mem4_ldu_stall || io.mem4_stu_stall
    }

    io.dcacheRefillUpdate.valid := io.dcacheRefillResp.valid
    io.dcacheRefillUpdate.index := io.dcacheRefillResp.pa.bits(
        p(DCacheIndexBits) + p(L1CacheOffsetBits) + p(FETCH_OFFSET) - 1, 
        p(L1CacheOffsetBits) + p(FETCH_OFFSET)
    )
    io.dcacheRefillUpdate.way := io.dcacheReplaceRefillResp.way

    io.mem3_mshr_stall := !io.mem3_dcache_mshr_enqReady
}