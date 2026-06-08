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
        val mem0_ld_bankconflict_stalls = Output(Vec(p(LDU_NUM), Bool()))
        val mem0_st_bankconflict_stalls = Output(Vec(p(STU_NUM), Bool()))
        // to store buffer
        val mem0_stu_result = Output(Vec(p(STU_NUM), new AGUResult))
        // to dcache
        val mem0_bank_reqs = Output(Vec(bankNum, new DCacheReq))
        val mem4_ldu_wb_stall = Input(Bool())
        val mem4_stu_wb_stall = Input(Bool())
        // stall from dtlb and mshr
        val mem2_dtlb_miss_infos = Input(Vec(bankNum, new DTLBMissInfo))
        val mem3_mshr_stall = Input(Bool()) 
        // from ex stage, to judge bank hit and send req to dcache
        val ex_ldu_result = Input(Vec(p(LDU_NUM), new AGUResult))
        val ex_stu_result = Input(Vec(p(STU_NUM), new AGUResult))
        // from dcache
        val mem3_dcache_hitInfos = Input(Vec(bankNum, new DCacheHitInfo))
        // to load / store wb queue
        val mem4_ldu_result = Output(Vec(p(LDU_NUM), new LoadWbQueueItem))
        val mem4_stu_result = Output(Vec(p(STU_NUM), new StoreWbQueueItem))
        // from load / store wb queue
        val mem4_ldu_deqs = Input(Vec(p(LDU_NUM), new LoadWbQueueItem))
        val mem4_stu_deqs = Input(Vec(p(STU_NUM), new StoreWbQueueItem))
        // agu result
        val mem4_ldu_result_o = Output(Vec(p(LDU_NUM), new LDUResult))
        // to dcache wb queue
        val mem4_ldu_stall = Input(Bool())
        val mem4_stu_stall = Input(Bool())
        val mem4_wb_enqs = Output(Vec(p(STU_NUM), new DCacheWriteBackReq))
    })

    val mem0_ld_stalls = WireInit(VecInit(
        Seq.fill(p(LDU_NUM))(false.B)
    ))

    val mem0_st_stalls = WireInit(VecInit(
        Seq.fill(p(LDU_NUM))(false.B)
    ))

    val mem1_ld_stalls = WireInit(VecInit(
        Seq.fill(p(LDU_NUM))(false.B)
    ))
    
    val mem1_st_stalls = WireInit(VecInit(
        Seq.fill(p(LDU_NUM))(false.B)
    ))

    val mem2_ld_stalls = WireInit(VecInit(
        Seq.fill(p(LDU_NUM))(false.B)
    ))
    
    val mem2_st_stalls = WireInit(VecInit(
        Seq.fill(p(LDU_NUM))(false.B)
    ))

    val mem3_ld_stalls = WireInit(VecInit(
        Seq.fill(p(LDU_NUM))(false.B)
    ))
    
    val mem3_st_stalls = WireInit(VecInit(
        Seq.fill(p(LDU_NUM))(false.B)
    ))

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
    val mem0_ldu_result = VecInit((0 until p(LDU_NUM)).map(i =>
        PipeReg(io.ex_ldu_result(i), mem0_ld_stalls(i), io.flush)
    ))

    val mem0_stu_result = VecInit((0 until p(LDU_NUM)).map(i =>
        PipeReg(io.ex_stu_result(i), mem0_st_stalls(i), io.flush)
    ))

    io.mem0_stu_result := mem0_stu_result
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
            mem0_skidBuffer_counters(b) + mem0_bank_hit_count(b) - 1.U, 
            Mux(
                mem0_need_stall_mask.reduce(_ || _),
                mem0_skidBuffer_counters(b),
                mem0_skidBuffer_counters(b) + mem0_bank_hit_count(b)
            )
        )

        val mem0_bank_req = WireInit(0.U.asTypeOf(new DCacheReq))
        mem0_bank_req.valid := mem0_has_bank_hits(b)
        mem0_bank_req.addr := mem0_results(mem0_bank_hit_idxs(b)).addr
        mem0_bank_req.wen := mem0_results(mem0_bank_hit_idxs(b)).wen
        mem0_bank_req.data := mem0_results(mem0_bank_hit_idxs(b)).wdata
        mem0_bank_req.mask := mem0_results(mem0_bank_hit_idxs(b)).mask
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
                skidBuffers(b)(mem0_bank_hit_prefix_sums(b)(i) - 1.U).addr := 
                    mem0_results(i).addr
                skidBuffers(b)(mem0_bank_hit_prefix_sums(b)(i) - 1.U).data := 
                    mem0_results(i).wdata
                skidBuffers(b)(mem0_bank_hit_prefix_sums(b)(i) - 1.U).isSignExt := 
                    mem0_results(i).is_sign_ext
                skidBuffers(b)(mem0_bank_hit_prefix_sums(b)(i) - 1.U).mask := 
                    mem0_results(i).mask
                skidBuffers(b)(mem0_bank_hit_prefix_sums(b)(i) - 1.U).robLoc := 
                    mem0_results(i).robLoc
                skidBuffers(b)(mem0_bank_hit_prefix_sums(b)(i) - 1.U).valid := 
                    mem0_results(i).valid
                skidBuffers(b)(mem0_bank_hit_prefix_sums(b)(i) - 1.U).wen := 
                    mem0_results(i).wen
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

    // mem1 stage
    val mem1_ldu_result = VecInit((0 until p(LDU_NUM)).map(i =>
        PipeReg(mem0_ldu_result(i), mem1_ld_stalls(i), io.flush)
    ))

    val mem1_stu_result = VecInit((0 until p(LDU_NUM)).map(i =>
        PipeReg(mem0_stu_result(i), mem1_st_stalls(i), io.flush)
    ))

    // mem2 stage
    val mem2_ldu_result = VecInit((0 until p(LDU_NUM)).map(i =>
        PipeReg(mem1_ldu_result(i), mem2_ld_stalls(i), io.flush)
    ))

    val mem2_stu_result = VecInit((0 until p(LDU_NUM)).map(i =>
        PipeReg(mem1_stu_result(i), mem2_st_stalls(i), io.flush)
    ))

    // mem3 stage
    val mem3_ldu_result = VecInit((0 until p(LDU_NUM)).map(i =>
        PipeReg(mem2_ldu_result(i), mem3_ld_stalls(i), io.flush)
    ))

    val mem3_stu_result = VecInit((0 until p(LDU_NUM)).map(i =>
        PipeReg(mem2_stu_result(i), mem3_st_stalls(i), io.flush)
    ))

    val mem3_ldu_enqs = WireInit(VecInit(
        Seq.fill(p(LDU_NUM))(0.U.asTypeOf(new LoadWbQueueItem))
    ))

    val mem3_stu_enqs = WireInit(VecInit(
        Seq.fill(p(STU_NUM))(0.U.asTypeOf(new StoreWbQueueItem))
    ))

    for(i <- 0 until p(LDU_NUM)) {
        mem3_ldu_enqs(i) := 0.U.asTypeOf(new LoadWbQueueItem)
        for(b <- 0 until bankNum) {
            when(
                io.mem3_dcache_hitInfos(b).valid && 
                io.mem3_dcache_hitInfos(b).robLoc === 
                    mem3_ldu_result(i).robLoc &&
                mem3_ldu_result(i).valid
            ) {
                mem3_ldu_enqs(i).HasRd := mem3_ldu_result(i).HasRd
                mem3_ldu_enqs(i).cacheLine := io.mem3_dcache_hitInfos(b).data
                mem3_ldu_enqs(i).hit := io.mem3_dcache_hitInfos(b).hit
                mem3_ldu_enqs(i).pa := io.mem3_dcache_hitInfos(b).pa
                mem3_ldu_enqs(i).pd := mem3_ldu_result(i).pd
                mem3_ldu_enqs(i).rd := mem3_ldu_result(i).rd
                mem3_ldu_enqs(i).robLoc := mem3_ldu_result(i).robLoc
                mem3_ldu_enqs(i).valid := mem3_ldu_result(i).valid
                mem3_ldu_enqs(i).is_sign_ext := mem3_ldu_result(i).is_sign_ext
                mem3_ldu_enqs(i).mask := io.mem3_dcache_hitInfos(b).mask
            }
        }
    }

    for(i <- 0 until p(STU_NUM)) {
        mem3_stu_enqs(i) := 0.U.asTypeOf(new StoreWbQueueItem)
        for(b <- 0 until bankNum) {
            when(
                io.mem3_dcache_hitInfos(b).valid && 
                io.mem3_dcache_hitInfos(b).robLoc === 
                    mem3_stu_result(i).robLoc &&
                mem3_stu_result(i).valid
            ) {
                mem3_stu_enqs(i).cacheLine := io.mem3_dcache_hitInfos(b).data
                mem3_stu_enqs(i).hit := io.mem3_dcache_hitInfos(b).hit
                mem3_stu_enqs(i).pa := io.mem3_dcache_hitInfos(b).pa
                mem3_stu_enqs(i).robLoc := mem3_stu_result(i).robLoc
                mem3_stu_enqs(i).valid := mem3_stu_result(i).valid
                mem3_stu_enqs(i).mask := io.mem3_dcache_hitInfos(b).mask
                mem3_stu_enqs(i).wdata := io.mem3_dcache_hitInfos(b).wdata
                mem3_stu_enqs(i).bankId := b.U
                mem3_stu_enqs(i).hitWay := io.mem3_dcache_hitInfos(b).hitWay
            }
        }
    }

    // mem4 stage, enq to load/store waiting queue
    // mem4 stage, deq item get real reg data
    val mem4_ldu_enqs = PipeReg(mem3_ldu_enqs, io.mem4_ldu_wb_stall, io.flush)
    val mem4_stu_enqs = PipeReg(mem3_stu_enqs, io.mem4_ldu_wb_stall, io.flush)

    val mem4_ldu_enqs_o = WireInit(VecInit(
        Seq.fill(p(LDU_NUM))(0.U.asTypeOf(new LoadWbQueueItem))
    ))

    val mem4_stu_enqs_o = WireInit(VecInit(
        Seq.fill(p(STU_NUM))(0.U.asTypeOf(new StoreWbQueueItem))
    ))

    for(i <- 0 until p(LDU_NUM)) {
        mem4_ldu_enqs_o(i) := mem4_ldu_enqs(i)
        // use mem3 hitinfo to update
        for(b <- 0 until bankNum) {
            when(
                io.mem3_dcache_hitInfos(b).valid && 
                io.mem3_dcache_hitInfos(b).robLoc === 
                    mem4_ldu_enqs(i).robLoc &&
                mem4_ldu_enqs(i).valid
            ) {
                mem4_ldu_enqs_o(i).HasRd := mem4_ldu_enqs(i).HasRd
                mem4_ldu_enqs_o(i).cacheLine := io.mem3_dcache_hitInfos(b).data
                mem4_ldu_enqs_o(i).hit := io.mem3_dcache_hitInfos(b).hit
                mem4_ldu_enqs_o(i).pa := io.mem3_dcache_hitInfos(b).pa
                mem4_ldu_enqs_o(i).pd := mem4_ldu_enqs(i).pd
                mem4_ldu_enqs_o(i).rd := mem4_ldu_enqs(i).rd
                mem4_ldu_enqs_o(i).robLoc := mem4_ldu_enqs(i).robLoc
                mem4_ldu_enqs_o(i).valid := mem4_ldu_enqs(i).valid
                mem4_ldu_enqs_o(i).is_sign_ext := mem4_ldu_enqs(i).is_sign_ext
                mem4_ldu_enqs_o(i).mask := io.mem3_dcache_hitInfos(b).mask
            }
        }
    }

    for(i <- 0 until p(STU_NUM)) {
        mem4_stu_enqs_o(i) := mem4_stu_enqs(i)
        // use mem3 hitinfo to update
        for(b <- 0 until bankNum) {
            when(
                io.mem3_dcache_hitInfos(b).valid && 
                io.mem3_dcache_hitInfos(b).robLoc === 
                    mem4_stu_enqs(i).robLoc &&
                mem4_stu_enqs_o(i).valid
            ) {
                mem4_stu_enqs_o(i).cacheLine := io.mem3_dcache_hitInfos(b).data
                mem4_stu_enqs_o(i).hit := io.mem3_dcache_hitInfos(b).hit
                mem4_stu_enqs_o(i).pa := io.mem3_dcache_hitInfos(b).pa
                mem4_stu_enqs_o(i).robLoc := mem4_stu_enqs(i).robLoc
                mem4_stu_enqs_o(i).valid := mem4_stu_enqs(i).valid
                mem4_stu_enqs_o(i).mask := io.mem3_dcache_hitInfos(b).mask
                mem4_stu_enqs_o(i).wdata := io.mem3_dcache_hitInfos(b).wdata
                mem4_stu_enqs_o(i).bankId := b.U
                mem4_stu_enqs_o(i).hitWay := io.mem3_dcache_hitInfos(b).hitWay
            }
        }
    }

    io.mem4_ldu_result := mem4_ldu_enqs_o
    io.mem4_stu_result := mem4_stu_enqs_o

    // load result
    for(i <- 0 until p(LDU_NUM)) {
        val lineIdx = io.mem4_ldu_deqs(i).pa.bits(
            p(FETCH_OFFSET) + p(L1CacheOffsetBits) - 1,
            p(FETCH_OFFSET)
        )
        val readData = io.mem4_ldu_deqs(i).cacheLine.datas(lineIdx)
        val readMask = io.mem4_ldu_deqs(i).mask

        val lduResultData = WireInit(0.U(p(DATA_WIDTH).W))

        when(readMask === "h000000FF".U) {
            lduResultData := Mux(io.mem4_ldu_deqs(i).is_sign_ext, Cat(Fill(24, readData(7)), readData(7, 0)), readData)
        }.elsewhen(readMask === "h0000FFFF".U) {
            lduResultData := Mux(io.mem4_ldu_deqs(i).is_sign_ext, Cat(Fill(16, readData(15)), readData(15, 0)), readData)
        }.otherwise {
            lduResultData := readData
        }

        io.mem4_ldu_result_o(i).HasRd := io.mem4_ldu_deqs(i).HasRd
        io.mem4_ldu_result_o(i).pd := io.mem4_ldu_deqs(i).pd
        io.mem4_ldu_result_o(i).rd := io.mem4_ldu_deqs(i).rd
        io.mem4_ldu_result_o(i).result := lduResultData
        io.mem4_ldu_result_o(i).robLoc := io.mem4_ldu_deqs(i).robLoc
        io.mem4_ldu_result_o(i).valid := io.mem4_ldu_deqs(i).valid
    }

    // store result
    for(i <- 0 until p(STU_NUM)) {
        val lineIdx = io.mem4_stu_deqs(i).pa.bits(
            p(FETCH_OFFSET) + p(L1CacheOffsetBits) - 1,
            p(FETCH_OFFSET)
        )
        val writeMask = io.mem4_stu_deqs(i).mask
        val writeData = io.mem4_stu_deqs(i).cacheLine.datas(lineIdx)
        val writtenData = WireInit(0.U(p(DATA_WIDTH).W))
        val wdata = io.mem4_stu_deqs(i).wdata

        when(writeMask === "h000000FF".U) {
            writtenData := Cat(writeData(p(DATA_WIDTH) - 1, 8), wdata(7, 0))
        }.elsewhen(writeMask === "h0000FFFF".U) {
            writtenData := Cat(writeData(p(DATA_WIDTH) - 1, 16), wdata(15, 0))
        }.otherwise {
            writtenData := writeData
        }

        val cacheLineResult = WireInit(0.U.asTypeOf(new DCacheLine))
        cacheLineResult := io.mem4_stu_deqs(i).cacheLine
        cacheLineResult.datas(lineIdx) := writtenData

        io.mem4_wb_enqs(i).valid := io.mem4_stu_deqs(i).valid
        io.mem4_wb_enqs(i).cacheLine := cacheLineResult
        io.mem4_wb_enqs(i).pa := io.mem4_stu_deqs(i).pa
        io.mem4_wb_enqs(i).bankId := io.mem4_stu_deqs(i).bankId
        io.mem4_wb_enqs(i).hitWay := io.mem4_stu_deqs(i).hitWay
    }

    for(i <- 0 until p(LDU_NUM)) {
        mem3_ld_stalls(i) := io.mem4_ldu_wb_stall || io.mem3_mshr_stall
        val mem2_dtlb_miss_mask = VecInit((0 until bankNum).map(b => 
            io.mem2_dtlb_miss_infos(b).valid && 
            io.mem2_dtlb_miss_infos(b).robLoc === mem2_ldu_result(i).robLoc &&
            mem2_ldu_result(i).valid
        ))
        mem2_ld_stalls(i) := mem3_ld_stalls(i) || mem2_dtlb_miss_mask.asUInt.orR
        mem1_ld_stalls(i) := mem2_ld_stalls(i)

        val mem0_bank_conflict_stall_mask = VecInit((0 until bankNum).map(b => 
            mem0_need_stall_mask(b) &&
            mem0_ldu_result(i).robLoc === io.mem0_bank_reqs(b).robLoc &&
            mem0_ldu_result(i).valid
        ))
        mem0_ld_stalls(i) := mem1_ld_stalls(i) || mem0_bank_conflict_stall_mask.asUInt.orR
    }

    for(i <- 0 until p(STU_NUM)) {
        mem3_st_stalls(i) := io.mem4_stu_wb_stall || io.mem3_mshr_stall
        val mem2_dtlb_miss_mask = VecInit((0 until bankNum).map(b => 
            io.mem2_dtlb_miss_infos(b).valid && 
            io.mem2_dtlb_miss_infos(b).robLoc === mem2_stu_result(i).robLoc &&
            mem2_stu_result(i).valid
        ))
        mem2_st_stalls(i) := mem3_st_stalls(i) || mem2_dtlb_miss_mask.asUInt.orR
        mem1_st_stalls(i) := mem2_st_stalls(i)

        val mem0_bank_conflict_stall_mask = VecInit((0 until bankNum).map(b => 
            mem0_need_stall_mask(b) &&
            mem0_stu_result(i).robLoc === io.mem0_bank_reqs(b).robLoc &&
            mem0_stu_result(i).valid
        ))
        mem0_st_stalls(i) := mem1_st_stalls(i) || mem0_bank_conflict_stall_mask.asUInt.orR
    }

    io.mem0_ld_bankconflict_stalls := mem0_ld_stalls
    io.mem0_st_bankconflict_stalls := mem0_st_stalls
}