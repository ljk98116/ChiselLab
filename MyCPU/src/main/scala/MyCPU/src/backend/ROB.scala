package MyCPU.backend

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

// dispatch1 enq 
// use rdy/valid as regs
// rob full will stall at rename1
// rob empty, not commit
// real rob not save results
class ROB(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val dispatch1_uops = Input(Vec(p(FETCH_WIDTH), new uOp))
        val inorder_locs = Output(Vec(p(FETCH_WIDTH), UInt(p(ROBID_WIDTH).W)))
        // from CDB
        val aluResult = Input(Vec(p(ALU_NUM), new ALUResult))
        val lduResult = Input(Vec(p(LDU_NUM), new LDUResult))
        val sysResult = Input(Vec(p(SYS_ALU_NUM), new SysResult))
        val multResult = Input(Vec(p(MULT_NUM), new ALUResult))
        val divResult = Input(Vec(p(DIV_NUM), new ALUResult))
        val remResult = Input(Vec(p(DIV_NUM), new ALUResult))

        val robHeadPtr = Output(UInt(p(ROBID_WIDTH).W))
        val robTailPtr = Output(UInt(p(ROBID_WIDTH).W))

        val loadQueueRobEnable = Output(Vec(p(FETCH_WIDTH), new LoadQueueEnable))
        val storeQueueRobEnable = Output(Vec(p(FETCH_WIDTH), new StoreQueueEnable))

        val commit1_load_raw_harvards_i = Input(Vec(p(LDU_NUM), new LoadRAWHarvardInfo)) // commit1 get
        val commit2_load_raw_harvards = DecoupledIO(new LoadRAWHarvardInfo)
        val commit_redirect = DecoupledIO(new CommitUpdateInfo)
        
        val rat_commit_update = Output(Vec(p(FETCH_WIDTH), new RATCommitWriteReq))

        // recycle free preg id to freeRegIdBuffer
        val freeRegIdBufferUpdate = Output(Vec(p(FETCH_WIDTH), new FreeRegIdBufferWriteReq))
        val freeRobIdBufferUpdate = Output(Vec(p(FETCH_WIDTH), new ROBFreeIdWriteReq))
    })

    val size = 1 << p(ROBID_WIDTH)
    val bankBits = log2Ceil(p(FETCH_WIDTH))
    val perBankSize = size / p(FETCH_WIDTH)
    val ptrWidth = log2Ceil(perBankSize)
    val bankNum = p(FETCH_WIDTH)

    val normal::rollback::Nil = Enum(2)
    val robState = RegInit(normal)
    val nextRobState = WireInit(normal)

    val headPtr = RegInit(0.U(ptrWidth.W))
    val tailPtr = RegInit(0.U(ptrWidth.W))

    val commit1_stall = WireInit(false.B)
    val commit2_stall = WireInit(false.B)

    val robBanks = Seq.tabulate(p(FETCH_WIDTH))(
        b => new LargeSyncBank(clock, reset, perBankSize, new uOp, "robBank_" + b.toString)
    )

    val rdyBanks = Seq.tabulate(p(FETCH_WIDTH))(
        b => RegInit(0.U(perBankSize.W))
    )
    val hasBranchPredErrBanks = Seq.tabulate(p(FETCH_WIDTH))(
        b => RegInit(0.U(perBankSize.W))
    )

    val IsLoadBanks = Seq.tabulate(p(FETCH_WIDTH))(
        b => RegInit(0.U(perBankSize.W))
    )
    val IsStoreBanks = Seq.tabulate(p(FETCH_WIDTH))(
        b => RegInit(0.U(perBankSize.W))
    )

    // stage0: headPtr/tailPtr/robState change
    val commit0_redirect = WireInit(false.B)
    // get redirect
    val commit0_rdys = VecInit((0 until p(FETCH_WIDTH)).map(b => 
        rdyBanks(b)(headPtr)
    ))

    val commit0_hasBranchPredErrs = VecInit((0 until p(FETCH_WIDTH)).map(b =>
        hasBranchPredErrBanks(b)(headPtr)
    ))

    val commit0_allow_commit = 
        commit0_rdys.asUInt.andR && // all commit items ready or not valid
        (headPtr =/= tailPtr) && // rob not empty
        robState === normal // robState is normal

    commit0_redirect := commit0_hasBranchPredErrs.asUInt.orR && commit0_allow_commit

    // change robState
    when(
        (robState === rollback) || // to do: add branch reset not ready cond
        (commit0_redirect && robState === normal) // robstate is normal and can commit, but need redirect
    ) {
        nextRobState := rollback
    }.elsewhen(
        (robState === rollback) || // to do: add branch reset ready cond
        (robState === normal && !commit0_redirect) // robState is normal and no redirect here
    ) {
        nextRobState := normal
    }
    
    robState := nextRobState

    // robPtrs io
    io.robHeadPtr := headPtr
    io.robTailPtr := tailPtr

    io.inorder_locs := VecInit((0 until p(FETCH_WIDTH)).map(b =>
        Cat(tailPtr(ptrWidth - 1, 0), b.U(bankBits.W))
    ))

    // update ROB with CDB Results
    val totalWritePorts = p(ALU_NUM) + p(LDU_NUM) + p(MULT_NUM) + 
        p(SYS_ALU_NUM) + p(DIV_NUM) * 2

    val alu_rdy_mask = (0 until p(ALU_NUM)).map(i =>
        io.aluResult(i).valid << io.aluResult(i).robLoc
    )
    val ldu_rdy_mask = (0 until p(LDU_NUM)).map(i => 
        io.lduResult(i).valid << io.lduResult(i).robLoc 
    )
    val mult_rdy_mask = (0 until p(MULT_NUM)).map(i =>
        io.multResult(i).valid << io.multResult(i).robLoc
    )
    val div_rdy_mask = (0 until p(DIV_NUM)).map(i =>
        io.divResult(i).valid << io.divResult(i).robLoc
    )
    val rem_rdy_mask = (0 until p(DIV_NUM)).map(i =>
        io.remResult(i).valid << io.remResult(i).robLoc
    )
    val sys_rdy_mask = (0 until p(SYS_ALU_NUM)).map(i =>
        io.sysResult(i).valid << io.sysResult(i).robLoc
    )    

    val update_rdy_mask = alu_rdy_mask.reduce(_|_) |
        ldu_rdy_mask.reduce(_|_) |
        sys_rdy_mask.reduce(_|_) |
        mult_rdy_mask.reduce(_|_) |
        div_rdy_mask.reduce(_|_) |
        rem_rdy_mask.reduce(_|_)

    // update rdy banks
    for(b <- 0 until p(FETCH_WIDTH)) {
        rdyBanks(b) := rdyBanks(b) | update_rdy_mask(perBankSize * (b + 1) - 1, perBankSize * b)
        // enq item is false
        when(io.dispatch1_uops(b).valid) {
            rdyBanks(b) := rdyBanks(b) & ~(1.U << io.inorder_locs(b)(p(ROBID_WIDTH) - 1, bankBits)).asUInt
            IsLoadBanks(b) := IsLoadBanks(b) | ((io.dispatch1_uops(b).opcode === OpcodeVal.LB).asUInt << io.inorder_locs(b)(p(ROBID_WIDTH) - 1, bankBits))
            IsStoreBanks(b) := IsStoreBanks(b) | ((io.dispatch1_uops(b).opcode === OpcodeVal.SB).asUInt << io.inorder_locs(b)(p(ROBID_WIDTH) - 1, bankBits))
        }
    }

    val alu_pred_err_mask = (0 until p(ALU_NUM)).map(i =>
        io.aluResult(i).hasPredErr << io.aluResult(i).robLoc
    ).reduce(_|_)

    // update hasBranchErrBanks
    for(b <- 0 until p(FETCH_WIDTH)) {
        hasBranchPredErrBanks(b) := hasBranchPredErrBanks(b) | alu_pred_err_mask(perBankSize * (b + 1) - 1, perBankSize * b)
        // enq item is false
        when(io.dispatch1_uops(b).valid) {
            hasBranchPredErrBanks(b) := hasBranchPredErrBanks(b) & ~(1.U << io.inorder_locs(b)(p(ROBID_WIDTH) - 1, bankBits)).asUInt
        }
    }
    
    for(i <- 0 until p(FETCH_WIDTH)) {
        io.loadQueueRobEnable(i).valid := IsLoadBanks(i)(headPtr)
        io.storeQueueRobEnable(i).valid := IsStoreBanks(i)(headPtr)

        io.loadQueueRobEnable(i).robLoc := Cat(headPtr, i.U(bankBits.W))
        io.storeQueueRobEnable(i).robLoc := Cat(headPtr, i.U(bankBits.W))
    }

    // stage1: rob read headptr, carry the robState)
    val commit1_robState = RegNext(robState)
    val commit1_uops = VecInit((0 until p(FETCH_WIDTH)).map(b =>
        robBanks(b).read(headPtr).asTypeOf(new uOp)    
    ))
    val commit1_hasBranchPredErrs = RegNext(commit0_hasBranchPredErrs)
    val commit1_redirect = RegNext(commit0_redirect)
    val commit1_allow_commit = RegNext(commit0_redirect)

    // get exception flags and exception Idx
    val commit1_branchPredErrIdx = PriorityEncoder(commit1_hasBranchPredErrs)
    val commit1_hasBranchPredError = commit1_hasBranchPredErrs.asUInt.orR

    // TO DO: get minIdx of exceptions occured
    val commit1_minRedirectIdx = commit1_branchPredErrIdx

    // stage2: rob inst commit or rollback, change stage0 robState
    // do inst commit and next rob state judge here
    val commit2_robState = RegNext(robState)
    val commit2_uops = RegNext(commit1_uops)
    val commit2_hasBranchPredErr = RegNext(commit1_hasBranchPredError)
    val commit2_branchPredErrIdx = RegNext(commit1_branchPredErrIdx)
    val commit2_allow_commit = RegNext(commit1_allow_commit)
    val commit2_redirect = RegNext(commit1_redirect)
    val commit2_minRedirectIdx = RegNext(commit1_minRedirectIdx)
    val commit2_load_raw_harvards = RegNext(io.commit1_load_raw_harvards_i)

    // generate full redirect msg
    io.commit_redirect.valid := commit2_redirect
    io.commit_redirect.bits.flush := commit2_redirect
    io.commit_redirect.bits.flush_pc := 0.U
    io.commit_redirect.bits.rasTopPtr := 
        commit2_uops(commit2_minRedirectIdx).branchPredInfo.rasTopPtr
    io.commit_redirect.bits.isCall := 
        commit2_uops(commit2_minRedirectIdx).branchPredInfo.isCall
    io.commit_redirect.bits.isReturn := 
        commit2_uops(commit2_minRedirectIdx).branchPredInfo.isReturn

    // TO DO: get minIdx of exceptions occured
    when(commit2_minRedirectIdx === commit2_branchPredErrIdx) {
        io.commit_redirect.bits.flush_pc := 
            commit2_uops(commit2_minRedirectIdx).branchPredInfo.target
    }
    io.commit_redirect.bits.pc := commit2_uops(commit2_minRedirectIdx).pc

    val load_raw_harvard_valids = VecInit((0 until p(LDU_NUM)).map(i =>
        commit2_load_raw_harvards(i).valid
    ))

    val load_raw_harvard_minIdx = PriorityEncoder(load_raw_harvard_valids)
    val has_load_raw_harvard = load_raw_harvard_valids.asUInt.orR

    io.commit2_load_raw_harvards.valid := has_load_raw_harvard
    io.commit2_load_raw_harvards.bits.valid := has_load_raw_harvard
    io.commit2_load_raw_harvards.bits.loadPC := commit2_load_raw_harvards(load_raw_harvard_minIdx).loadPC
    io.commit2_load_raw_harvards.bits.storePC := commit2_load_raw_harvards(load_raw_harvard_minIdx).storePC
    io.commit2_load_raw_harvards.bits.ldRobLoc := commit2_load_raw_harvards(load_raw_harvard_minIdx).ldRobLoc

    for(i <- 0 until p(FETCH_WIDTH)) {
        io.rat_commit_update(i).areg := commit2_uops(i).rd
        io.rat_commit_update(i).preg := commit2_uops(i).pd
        io.rat_commit_update(i).valid := commit2_uops(i).HasRd

        io.freeRegIdBufferUpdate(i).preg := commit2_uops(i).old_pd
        io.freeRegIdBufferUpdate(i).valid := commit2_uops(i).HasRd

        io.freeRobIdBufferUpdate(i).robId := commit2_uops(i).rob_id
        io.freeRegIdBufferUpdate(i).valid := commit2_uops(i).valid
    }

    // commit allowed or rollback not over
    when(commit2_redirect) {
        headPtr := 0.U
    }.elsewhen(commit0_allow_commit || (robState === rollback && headPtr =/= tailPtr)) {
        headPtr := headPtr + 1.U
    }

    val dispatch1_uop_valids = VecInit(io.dispatch1_uops.map(_.valid))
    val canEnq = (tailPtr + 1.U) =/= headPtr

    when(commit2_redirect) {
        tailPtr := 0.U
    }.elsewhen(dispatch1_uop_valids.asUInt.orR && canEnq && robState === normal) {
        tailPtr := tailPtr + 1.U    
    }

    // do commit here, may interect with emulator
    when(commit2_allow_commit) {
        
    }
}


