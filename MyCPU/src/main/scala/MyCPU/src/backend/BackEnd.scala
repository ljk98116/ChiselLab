package MyCPU.backend

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._
import MyCPU.cache._

class BackEnd(implicit p: Parameters) extends Module {
    val readPortNum = p(LDU_NUM)
    val writePortNum = p(STU_NUM)
    val bankNum = writePortNum + readPortNum
    // use writePortNum as bankNum
    // copy readPortNum within a bank
    val bankBits = log2Ceil(bankNum)
    val preg_num = 1 << p(PREG_WIDTH)

    val io = IO(new Bundle {
        val commit_redirect = DecoupledIO(new CommitUpdateInfo)
        // from dispatch
        val ldu_uops = Input(Vec(p(FETCH_WIDTH), new uOp))
        val stu_uops = Input(Vec(p(FETCH_WIDTH), new uOp))
        val alu_uops = Input(Vec(p(FETCH_WIDTH), new uOp))
        val mult_uops = Input(Vec(p(FETCH_WIDTH), new uOp))
        val div_uops = Input(Vec(p(FETCH_WIDTH), new uOp))
        val rem_uops = Input(Vec(p(FETCH_WIDTH), new uOp))
        val sys_uops = Input(Vec(p(FETCH_WIDTH), new uOp))
        // to middle end
        val issue0_stall = Output(Bool())
        val freeRegIdBufferResp = Input(Vec(p(FETCH_WIDTH), new FreeRegIdBufferReadResp))
        val busyTableCheckPoint = Input(new CheckPointInfo)
        val busyTableCheckPoints = Output(Vec(p(FETCH_WIDTH), UInt(preg_num.W)))
        val commit_refill_write_back = DecoupledIO(Vec((1 << p(AREG_WIDTH)), new RATCommitEntry))
        val freeRegIdBufferUpdate = Output(Vec(p(FETCH_WIDTH), new FreeRegIdBufferWriteReq))
        val freeRobIdBufferUpdate = Output(Vec(p(FETCH_WIDTH), new ROBFreeIdWriteReq))
        val robHeadPtr = Output(UInt(p(ROBID_WIDTH).W))
        val robTailPtr = Output(UInt(p(ROBID_WIDTH).W))
        // from middle end
        val dispatch1_uops = Input(Vec(p(FETCH_WIDTH), new uOp))
        val inorder_locs = Output(Vec(p(FETCH_WIDTH), UInt(p(ROBID_WIDTH).W)))
        // dcache
        val mem0_bank_reqs = Output(Vec(bankNum, new DCacheReq))
        val mem4_ldu_wb_stall = Input(Bool())
        val mem4_stu_wb_stall = Input(Bool())
        val dcache_refill_resp = Input(new DCacheRefillResp)
        val dcache_way_refill_resp = Input(new ReplacePolicyRefillRespInfo)
        // from dcache
        val mem3_dcache_hitInfos = Input(Vec(bankNum, new DCacheHitInfo))
        // to dcache wb queue
        val mem4_wb_enqs = Output(Vec(p(STU_NUM), new DCacheWriteBackReq))
        val storeBufferDeqEnables = Input(Vec(p(STU_NUM), Bool()))
        val storeBufferDeqs = Output(Vec(p(STU_NUM), new AGUResult))
        // stall from dtlb and mshr
        val mem2_dtlb_miss_infos = Input(Vec(bankNum, new DTLBMissInfo))
        val mem3_mshr_stall = Input(Bool())
        // load / store reached rob top / rob
        val load_raw_harvards = DecoupledIO(new LoadRAWHarvardInfo)
    })

    val issueStages = Module(new IssueStages)
    val regReadStage = Module(new RegReadStage)
    val prf = Module(new PRF)
    val exStage = Module(new EXStage)
    val memStages = Module(new MemStages)
    val loadWbQueue = Module(new LoadWbQueue(p(DCacheMSHRNum)))
    val storeWbQueue = Module(new StoreWbQueue(p(DCacheMSHRNum)))
    
    val loadBuffer = Module(new LoadBuffer(p(DCacheMSHRNum) * 4))
    val storeBuffer = Module(new StoreBuffer(p(DCacheMSHRNum) * 4))
    val ratCommit = Module(new RATCommit)
    val rob = Module(new ROB)

    // issueStages io
    issueStages.io.flush := rob.io.commit_redirect.valid
    io.issue0_stall := issueStages.io.issue0_stall

    issueStages.io.ldu_uops := io.ldu_uops
    issueStages.io.stu_uops := io.stu_uops
    issueStages.io.alu_uops := io.alu_uops
    issueStages.io.sys_uops := io.sys_uops
    issueStages.io.mult_uops := io.mult_uops
    issueStages.io.div_uops := io.div_uops
    issueStages.io.rem_uops := io.rem_uops

    issueStages.io.loadQueueRobEnable := rob.io.loadQueueRobEnable
    issueStages.io.storeQueueRobEnable := rob.io.storeQueueRobEnable

    issueStages.io.busyTableVec := prf.io.busyTable

    issueStages.io.stall_alu_iqs := regReadStage.io.stall_alu_iqs
    issueStages.io.stall_ldu_iqs := regReadStage.io.stall_ldu_iqs
    issueStages.io.stall_stu_iqs := regReadStage.io.stall_stu_iqs
    issueStages.io.stall_sys_iqs := regReadStage.io.stall_sys_iqs
    issueStages.io.stall_mult_iqs := regReadStage.io.stall_mult_iqs
    issueStages.io.stall_div_iqs := regReadStage.io.stall_div_iqs
    issueStages.io.stall_rem_iqs := regReadStage.io.stall_rem_iqs

    // regReadStage io
    regReadStage.io.flush := rob.io.commit_redirect.valid

    regReadStage.io.ldu_items := issueStages.io.ldu_items
    regReadStage.io.stu_items := issueStages.io.stu_items
    regReadStage.io.alu_items := issueStages.io.alu_items
    regReadStage.io.sys_items := issueStages.io.sys_items
    regReadStage.io.mult_items := issueStages.io.mult_items
    regReadStage.io.div_items := issueStages.io.div_items
    regReadStage.io.rem_items := issueStages.io.rem_items

    regReadStage.io.aluResult := exStage.io.aluResult
    regReadStage.io.multResult := exStage.io.multResult
    regReadStage.io.divResult := exStage.io.divResult
    regReadStage.io.remResult := exStage.io.remResult
    regReadStage.io.sysResult := exStage.io.sysResult
    regReadStage.io.lduResult := memStages.io.mem5_ldu_result_o

    regReadStage.io.multReadyVec := exStage.io.multReadyVec
    regReadStage.io.divReadyVec := exStage.io.divReadyVec
    regReadStage.io.remReadyVec := exStage.io.remReadyVec

    regReadStage.io.alu_stalls := prf.io.stall_wb1_alus
    regReadStage.io.ldu_stalls := prf.io.stall_wb1_ldus
    regReadStage.io.sys_stalls := prf.io.stall_wb1_sys
    regReadStage.io.mult_stalls := prf.io.stall_wb1_mult
    regReadStage.io.div_stalls := prf.io.stall_wb1_div
    regReadStage.io.rem_stalls := prf.io.stall_wb1_rem

    regReadStage.io.mem0_ld_bank_conflict_stalls := memStages.io.mem0_ld_bankconflict_stalls
    regReadStage.io.mem0_st_bank_conflict_stalls := memStages.io.mem0_st_bankconflict_stalls

    // exStage io
    exStage.io.flush := rob.io.commit_redirect.valid

    exStage.io.ldu_items := regReadStage.io.ldu_items_ready
    exStage.io.stu_items := regReadStage.io.stu_items_ready
    exStage.io.alu_items := regReadStage.io.alu_items_ready
    exStage.io.sys_items := regReadStage.io.sys_items_ready
    exStage.io.mult_items := regReadStage.io.mult_items_ready
    exStage.io.div_items := regReadStage.io.div_items_ready
    exStage.io.rem_items := regReadStage.io.rem_items_ready

    exStage.io.alu_stalls := prf.io.stall_wb1_alus
    exStage.io.ldu_stalls := prf.io.stall_wb1_ldus
    exStage.io.sys_stalls := prf.io.stall_wb1_sys
    exStage.io.mult_stalls := prf.io.stall_wb1_mult
    exStage.io.div_stalls := prf.io.stall_wb1_div
    exStage.io.rem_stalls := prf.io.stall_wb1_rem

    exStage.io.mem0_ld_bank_conflict_stalls := memStages.io.mem0_ld_bankconflict_stalls
    exStage.io.mem0_st_bank_conflict_stalls := memStages.io.mem0_st_bankconflict_stalls
    
    exStage.io.integer_inputs := regReadStage.io.integer_inputs
    exStage.io.branch_inputs := regReadStage.io.branch_inputs
    exStage.io.mult_inputs := regReadStage.io.mult_inputs
    exStage.io.div_inputs := regReadStage.io.div_inputs
    exStage.io.rem_inputs := regReadStage.io.rem_inputs

    // memStages io
    memStages.io.flush := rob.io.commit_redirect.valid

    memStages.io.mem0_storeBufferCanEnq := storeBuffer.io.mem0_storeBufferCanEnq
    memStages.io.mem2_storeBuffer_resps := storeBuffer.io.mem2_storeBuffer_resps

    memStages.io.mem0_loadBufferCanEnq := loadBuffer.io.mem0_loadBufferCanEnq

    memStages.io.mem4_ldu_wb_stall := io.mem4_ldu_wb_stall
    memStages.io.mem4_stu_wb_stall := io.mem4_stu_wb_stall
    io.mem0_bank_reqs := memStages.io.mem0_bank_reqs
    memStages.io.mem3_dcache_hitInfos := io.mem3_dcache_hitInfos
    io.mem4_wb_enqs := memStages.io.mem4_wb_enqs

    memStages.io.mem2_dtlb_miss_infos := io.mem2_dtlb_miss_infos
    memStages.io.mem3_mshr_stall := io.mem3_mshr_stall

    memStages.io.ex_ldu_result := exStage.io.lduResult
    memStages.io.ex_stu_result := exStage.io.stuResult

    memStages.io.mem5_ldu_deqs := loadWbQueue.io.mem5_ldu_deqs
    memStages.io.mem5_stu_deqs := storeWbQueue.io.mem5_stu_deqs

    // store buffer io
    storeBuffer.io.flush := rob.io.commit_redirect.valid
    storeBuffer.io.mem0_stu_results := memStages.io.mem0_stu_result
    storeBuffer.io.mem1_ldu_results := memStages.io.mem1_ld_results
    storeBuffer.io.mem2_ld_stalls := memStages.io.mem2_ld_stalls
    storeBuffer.io.storeBufferDeqEnables := io.storeBufferDeqEnables
    io.storeBufferDeqs := storeBuffer.io.storeBufferDeqs

    // load buffer io
    loadBuffer.io.flush := rob.io.commit_redirect.valid
    loadBuffer.io.mem0_ldu_results := memStages.io.mem0_ldu_result
    loadBuffer.io.mem1_stu_results := memStages.io.mem1_st_results
    loadBuffer.io.mem2_st_stalls := memStages.io.mem2_st_stalls
    loadBuffer.io.mem5_ldu_deqs := memStages.io.mem5_ldu_deqs

    loadBuffer.io.loadBufferRobEnables := rob.io.loadQueueRobEnable
    loadBuffer.io.robHeadPtr := rob.io.robHeadPtr
    loadBuffer.io.robTailPtr := rob.io.robTailPtr
    
    // store wb queue io
    storeWbQueue.io.flush := rob.io.commit_redirect.valid
    storeWbQueue.io.mem4_stu_result := memStages.io.mem4_stu_result
    memStages.io.mem5_stu_deqs := storeWbQueue.io.mem5_stu_deqs
    storeWbQueue.io.dcache_refill_resp := io.dcache_refill_resp
    storeWbQueue.io.dcache_way_refill_resp := io.dcache_way_refill_resp

    // load wb queue io
    loadWbQueue.io.flush := rob.io.commit_redirect.valid
    loadWbQueue.io.mem4_ldu_result := memStages.io.mem4_ldu_result
    memStages.io.mem5_ldu_deqs := loadWbQueue.io.mem5_ldu_deqs
    loadWbQueue.io.dcache_refill_resp := io.dcache_refill_resp
    loadWbQueue.io.dcache_way_refill_resp := io.dcache_way_refill_resp

    // prf io
    prf.io.flush := rob.io.commit_redirect.valid
    prf.io.prf_read_reqs_1 := regReadStage.io.prf_read_reqs_1
    prf.io.prf_read_reqs_2 := regReadStage.io.prf_read_reqs_2

    prf.io.aluResult := exStage.io.aluResult
    prf.io.lduResult := memStages.io.mem5_ldu_result_o
    prf.io.sysResult := exStage.io.sysResult
    prf.io.multResult := exStage.io.multResult
    prf.io.divResult := exStage.io.divResult
    prf.io.remResult := exStage.io.remResult

    prf.io.freeRegIdBufferResp := io.freeRegIdBufferResp
    prf.io.busyTableCheckPoint := io.busyTableCheckPoint

    io.busyTableCheckPoints := prf.io.busyTableCheckPoints

    regReadStage.io.prf_read_resp_1 := prf.io.prf_read_resp_1
    regReadStage.io.prf_read_resp_2 := prf.io.prf_read_resp_2

    // rob io
    rob.io.dispatch1_uops := io.dispatch1_uops
    io.inorder_locs := rob.io.inorder_locs

    rob.io.aluResult := exStage.io.aluResult
    rob.io.lduResult := memStages.io.mem5_ldu_result_o
    rob.io.sysResult := exStage.io.sysResult
    rob.io.multResult := exStage.io.multResult
    rob.io.divResult := exStage.io.divResult
    rob.io.remResult := exStage.io.remResult

    rob.io.commit1_load_raw_harvards_i := loadBuffer.io.load_raw_harvards

    io.load_raw_harvards <> rob.io.commit2_load_raw_harvards

    io.commit_redirect <> rob.io.commit_redirect

    io.freeRegIdBufferUpdate := rob.io.freeRegIdBufferUpdate
    io.freeRobIdBufferUpdate := rob.io.freeRobIdBufferUpdate

    io.robHeadPtr := rob.io.robHeadPtr
    io.robTailPtr := rob.io.robTailPtr

    // ratCommit io
    ratCommit.io.flush := rob.io.commit_redirect.valid
    ratCommit.io.rd_rat_write_req := rob.io.rat_commit_update

    io.commit_refill_write_back <> ratCommit.io.commit_refill_write_back
}