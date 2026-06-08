package MyCPU.backend

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._
import MyCPU.cache._

class MemStages(implicit p: Parameters) extends Module {
    val readPortNum = p(LDU_NUM)
    val writePortNum = p(STU_NUM)
    val bankNum = writePortNum + readPortNum
    // use writePortNum as bankNum
    // copy readPortNum within a bank
    val bankBits = log2Ceil(bankNum)

    val io = IO(new Bundle {
        val flush = Input(Bool())
        val mem0_bankconflict_stall = Output(Bool())
        // to store buffer
        val mem0_st_result = Output(Vec(p(STU_NUM), new AGUResult))
        // to dcache
        val mem0_bank_reqs = Output(Vec(bankNum, new DCacheReq))
        val mem4_stall = Output(Bool())
        // stall from dtlb and mshr
        val mem2_dtlb_miss_stall = Input(Bool())
        val mem3_mshr_stall = Input(Bool()) 
        // from ex stage, to judge bank hit and send req to dcache
        val ex_ldu_result = Input(Vec(p(LDU_NUM), new AGUResult))
        val ex_stu_result = Input(Vec(p(STU_NUM), new AGUResult))
        // from dcache
        val mem3_dcache_hitInfos = Input(Vec(bankNum, new DCacheHitInfo))
    })

    val mem0_stall = WireInit(false.B)
    val mem1_stall = WireInit(false.B)
    val mem2_stall = WireInit(false.B)
    val mem3_stall = WireInit(false.B)

    val skidBuffers = Seq.fill(bankNum)(
        RegInit(VecInit(
            Seq.fill(bankNum)(0.U.asTypeOf(new DCacheReq))
        ))
    )

    val mem0_bank_hit_mask = Seq.fill(bankNum)(
        WireInit(VecInit(
            Seq.fill(bankNum)(false.B)
        ))
    )

    val mem0_has_bank_hits = Seq.fill(bankNum)(
        WireInit(false.B)
    )

    val mem0_bank_hit_idxs = Seq.fill(bankNum)(
        WireInit(0.U(bankBits.W))
    )

    val mem0_bank_hit_prefix_sums = Seq.fill(bankNum)(
        WireInit(VecInit(
            Seq.fill(bankNum)(0.U(bankBits.W))
        ))
    )

    val mem0_skidBuffer_counters = Seq.fill(bankNum)(
        RegInit(0.U(bankBits.W))
    )

    val mem0_bank_hit_count = Seq.fill(bankNum)(
        WireInit(0.U(bankBits.W))
    )

    val mem0_need_stall_mask = Seq.fill(bankNum)(
        WireInit(false.B)
    )

    val mem0_results = WireInit(VecInit(
        Seq.fill(bankNum)(0.U.asTypeOf(new AGUResult))
    ))
    // mem0 need stage current ex result
    val mem0_ldu_result = PipeReg(io.ex_ldu_result, mem0_stall, io.flush)
    val mem0_stu_result = PipeReg(io.ex_stu_result, mem0_stall, io.flush)

    // mem0 ex result 
    for(i <- 0 until bankNum) {
        if(i < writePortNum) {
            mem0_results(i) := mem0_stu_result(i)
        } else {
            mem0_results(i) := mem0_ldu_result(i - writePortNum)
        }
    }

    // mem0 judge bank conflict, if conflict stage to skidbuffer
    // if skidbuffer full, stall mem0
    for(b <- 0 until bankNum) {
        // get bank hit mask for each port
        for(i <- 0 until bankNum) {
            if(i < writePortNum) {
                mem0_bank_hit_mask(b)(i) := 
                    mem0_stu_result(i).valid && 
                    mem0_stu_result(i).addr(
                        p(FETCH_OFFSET) + p(L1CacheOffsetBits) + bankBits - 1,
                        p(FETCH_OFFSET) + p(L1CacheOffsetBits)
                    ) === b.U
            } else {
                mem0_bank_hit_mask(b)(i) := 
                    mem0_ldu_result(i - writePortNum).valid && 
                    mem0_ldu_result(i - writePortNum).addr(
                        p(FETCH_OFFSET) + p(L1CacheOffsetBits) + bankBits - 1,
                        p(FETCH_OFFSET) + p(L1CacheOffsetBits)
                    ) === b.U
            }
        }

        // if have bank hit, get hit idx
        mem0_has_bank_hits(b) := mem0_bank_hit_mask(b).asUInt.orR
        mem0_bank_hit_idxs(b) := PriorityEncoder(mem0_bank_hit_mask(b))
        mem0_bank_hit_count(b) := PopCount(mem0_bank_hit_mask(b))
        mem0_need_stall_mask(b) := 
            mem0_skidBuffer_counters(b) =/= 0.U &&
            mem0_skidBuffer_counters(b) + mem0_bank_hit_count(b) >= bankNum.U

        mem0_bank_hit_prefix_sums(b)(0) := 0.U
        for(i <- 1 until bankNum) {
            mem0_bank_hit_prefix_sums(b)(i) := PopCount(mem0_bank_hit_mask(b).asUInt(i-1, 0))
        }

        mem0_skidBuffer_counters(b) := Mux(
            mem0_skidBuffer_counters(b) === 0.U,
            Mux(
                mem0_has_bank_hits(b), 
                mem0_bank_hit_count(b) - 1.U, 
                0.U
            ),
            Mux(
                mem0_need_stall_mask.reduce(_ || _),
                mem0_skidBuffer_counters(b),
                mem0_skidBuffer_counters(b) + mem0_bank_hit_count(b)
            )
        )

        val mem0_bank_req = WireInit(0.U.asTypeOf(new DCacheReq))
        mem0_bank_req.valid := mem0_has_bank_hits(b)
        mem0_bank_req.addr := mem0_results(mem0_bank_hit_idxs(b)).addr
        mem0_bank_req.wen := !mem0_results(mem0_bank_hit_idxs(b)).HasRd
        mem0_bank_req.data := mem0_results(mem0_bank_hit_idxs(b)).wdata
        mem0_bank_req.mask := mem0_results(mem0_bank_hit_idxs(b)).mask
        mem0_bank_req.pipeId := b.U
        mem0_bank_req.robLoc := mem0_results(mem0_bank_hit_idxs(b)).robLoc
        mem0_bank_req.isSignExt := mem0_results(mem0_bank_hit_idxs(b)).is_sign_ext

        io.mem0_bank_reqs(b) := Mux(
            mem0_skidBuffer_counters(b) === 0.U,
            Mux(
                mem0_has_bank_hits(b), 
                mem0_bank_req,
                0.U.asTypeOf(new DCacheReq)
            ),
            skidBuffers(b)(0)
        )

        // update skidBuffer
        for(i <- 0 until bankNum) {
            when(
                mem0_skidBuffer_counters(b) === 0.U &&
                mem0_bank_hit_prefix_sums(b)(i) =/= 0.U
            ) {
                skidBuffers(b)(mem0_bank_hit_prefix_sums(b)(i) - 1.U) :=
                    mem0_results(i)
            }.elsewhen(
                mem0_skidBuffer_counters(b) =/= 0.U && 
                !mem0_need_stall_mask(b)
            ) {
                skidBuffers(b)(
                    mem0_bank_hit_prefix_sums(b)(i) + 
                    mem0_skidBuffer_counters(b)
                ) := skidBuffers(b)(i)
            }
        }
    }

    //bank conflict stall
    mem0_stall := 
        mem0_need_stall_mask.reduce(_ || _) || 
        io.mem2_dtlb_miss_stall || 
        io.mem3_mshr_stall

    io.mem0_bankconflict_stall := mem0_stall

    mem1_stall := io.mem2_dtlb_miss_stall || io.mem3_mshr_stall
    mem2_stall := io.mem2_dtlb_miss_stall || io.mem3_mshr_stall
    mem3_stall := io.mem3_mshr_stall

    // mem1 stage
    val mem1_ldu_result = PipeReg(mem0_ldu_result, mem1_stall, io.flush)
    val mem1_stu_result = PipeReg(mem0_stu_result, mem1_stall, io.flush)

    // mem2 stage
    val mem2_ldu_result = PipeReg(mem1_ldu_result, mem2_stall, io.flush)
    val mem2_stu_result = PipeReg(mem1_stu_result, mem2_stall, io.flush)

    // mem3 stage
    val mem3_ldu_result = PipeReg(mem2_ldu_result, mem3_stall, io.flush)
    val mem3_stu_result = PipeReg(mem2_stu_result, mem3_stall, io.flush)

    // mem4 stage, enq to load/store waiting queue
    // wait for dcache hit info or refill result
    val mem4_ldu_result = PipeReg(mem3_ldu_result, io.mem4_stall, io.flush)
    val mem4_stu_result = PipeReg(mem3_stu_result, io.mem4_stall, io.flush)

    
}