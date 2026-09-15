package MyCPU.frontend.BPU

import chisel3._
import chisel3.util._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._
import org.chipsalliance.cde.config._

// update when commit
// if1 stage access and get hitVec 
// if2 stage generate target and hit info, if miss generate pc + 4

// use L1 BTB WbQueue to update L0 BTB
// fifo select victim way
// area: 4475.240000, top freq: 2354MHz
class L1BTB(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val if1_flush = Input(Bool())
        val if2_flush = Input(Bool())
        // from if1 stage
        val if1_reqPC = Input(Vec(p(FETCH_WIDTH), UInt(p(ADDR_WIDTH).W)))
        val if1_reqValid = Input(Vec(p(FETCH_WIDTH), Bool()))
        // from if2 stage
        val if2_reqPC = Input(Vec(p(FETCH_WIDTH), UInt(p(ADDR_WIDTH).W)))
        val if2_reqValid = Input(Vec(p(FETCH_WIDTH), Bool()))

        val if1_stall = Input(Bool())
        val if2_stall = Input(Bool())

        val req = Input(Vec(p(FETCH_WIDTH), new L1BTBReqInfo))
        val resp = Output(new L1BTBRespInfo)
        val update = Input(Vec(p(FETCH_WIDTH), new L1BTBUpdateInfo)) // no bank conflict update
        val if2_l1_btb_hit = Output(Vec(p(FETCH_WIDTH), Bool()))
    })

    val wayNum = p(L1BTBParam).btbWayNum

    val EntryNum = p(L1BTBParam).btbEntryNum
    val tagBits = p(L1BTBParam).btbTagBits
    val indexBits = p(L1BTBParam).btbIndexBits
    val offsetBits = p(L1BTBParam).btbOffsetBits
    val bankNum = p(L1BTBParam).btbBankNum
    
    require(bankNum == p(FETCH_WIDTH), "l1 btb bankNum must equal to fetch width")

    val bankBits = log2Ceil(bankNum)
    
    val l1_btb_mem = Seq.tabulate(bankNum)(b => 
        Seq.tabulate(wayNum)(
            w => new LargeSyncBank(clock, reset, EntryNum / bankNum / wayNum, new L1BTBEntry, "l1btbbank_" + b.toString + w.toString)
        )
    )

    val numRows = EntryNum / bankNum / wayNum

    val if1_hitVec = WireInit(VecInit(
        Seq.fill(bankNum)(
            VecInit(Seq.fill(wayNum)(false.B))
        )
    ))

    val if1_isCallVec = WireInit(VecInit(
        Seq.fill(bankNum)(
            VecInit(Seq.fill(wayNum)(false.B))
        )
    ))

    val if1_isReturnVec = WireInit(VecInit(
        Seq.fill(bankNum)(
            VecInit(Seq.fill(wayNum)(false.B))
        )
    ))

    val if1_validVec = WireInit(VecInit(
        Seq.fill(bankNum)(
            VecInit(Seq.fill(wayNum)(false.B))
        )
    ))

    val if1_targetVec = WireInit(VecInit(
        Seq.fill(bankNum)(VecInit(
            Seq.fill(wayNum)(0.U((p(ADDR_WIDTH) / 4).W))
        ))
    ))

    val if2_hitVec = PipeReg(if1_hitVec, io.if2_stall, io.if2_flush)
    val if2_targetVec = PipeReg(if1_targetVec, io.if2_stall, io.if2_flush)
    val if2_isCallVec = PipeReg(if1_isCallVec, io.if2_stall, io.if2_flush)
    val if2_isReturnVec = PipeReg(if1_isReturnVec, io.if2_stall, io.if2_flush)
    
    val if2_hitIndex = WireInit(0.U(log2Ceil(p(FETCH_WIDTH)).W))
    dontTouch(if2_hitIndex)

    // read at if0 stage, use io.req
    // decide the bth bank use which reqPC
    // bank conflict can not happen, because fetch pack must be adjcent pcs
    for(b <- 0 until bankNum) {
        val bankId = io.req(b).pc(bankBits + offsetBits - 1, offsetBits)
        val bankRow = io.req(b).pc(indexBits + offsetBits - 1, offsetBits + bankBits)

        val if1_pcTag = PipeReg(io.req(b).pc(tagBits + indexBits + offsetBits - 1, indexBits + offsetBits), 
            io.if1_stall, io.if1_flush)
        
        val if1_reqValid = PipeReg(io.req(b).valid, io.if1_stall, io.if1_flush)

        when(io.req(b).valid) {
            assert(bankId === b.U,
                "l1 btb access(%d) targets bank %d, expected bank %d", b.U, bankId, b.U)
        }

        for(w <- 0 until wayNum) {
            val if1_memReadResult = Mux(
                if1_reqValid,
                l1_btb_mem(b)(w).read(bankRow).asTypeOf(new L1BTBEntry),
                0.U.asTypeOf(new L1BTBEntry)
            )   

            val if1_tag = if1_memReadResult.tag
            val valid = if1_memReadResult.valid
            val isCall = if1_memReadResult.isCall
            val isReturn = if1_memReadResult.isReturn

            // hit judge at if1 stage, use staged req
            if1_hitVec(b)(w) := 
                if1_tag === if1_pcTag && valid
            if1_validVec(b)(w) := valid
            if1_isCallVec(b)(w) := isCall
            if1_isReturnVec(b)(w) := isReturn
            if1_targetVec(b)(w) := if1_memReadResult.target
        }
    }

    // double PE may be a critical timing path
    if(wayNum > 1) {
        val if2_hitWayArray = WireInit(VecInit(Seq.tabulate(bankNum)(b => PriorityEncoder(if2_hitVec(b)))))
        val if2_hasHitWayArray = Seq.tabulate(bankNum)(b => if2_hitVec(b).reduce(_ || _))
        if2_hitIndex := PriorityEncoder(if2_hasHitWayArray)
        
        io.resp.valid := if2_hasHitWayArray.reduce(_ || _)

        io.resp.target := Mux(
            io.resp.valid, 
            if2_targetVec(if2_hitIndex)(if2_hitWayArray(if2_hitIndex)), 
            io.if2_reqPC(0) + (p(FETCH_WIDTH) << 2).U
        )
        io.resp.isCall := Mux(io.resp.valid, if2_isCallVec(if2_hitIndex)(if2_hitWayArray(if2_hitIndex)), false.B)
        io.resp.isReturn := Mux(io.resp.valid, if2_isReturnVec(if2_hitIndex)(if2_hitWayArray(if2_hitIndex)), false.B)

        io.resp.hitIndex := if2_hitIndex
        io.if2_l1_btb_hit := if2_hasHitWayArray
    }
    else {
        val if2_hitArray = Seq.tabulate(bankNum)(i => if2_hitVec(i)(0))
        if2_hitIndex := PriorityEncoder(if2_hitArray)

        io.resp.valid := VecInit(if2_hitArray).asUInt.orR        
        io.resp.target := Mux(io.resp.valid, if2_targetVec(if2_hitIndex)(0), io.if2_reqPC(0) + (p(FETCH_WIDTH) << 2).U)
        io.resp.isCall := Mux(io.resp.valid, if2_isCallVec(if2_hitIndex)(0), false.B)
        io.resp.isReturn := Mux(io.resp.valid, if2_isReturnVec(if2_hitIndex)(0), false.B)
        io.resp.hitIndex := if2_hitIndex
        io.if2_l1_btb_hit := if2_hitArray
    }

    // same update logic for hit and update
    val updatePC = io.update.map(_.pc)
    val updateIsCall = io.update.map(_.isCall)
    val updateIsReturn = io.update.map(_.isReturn)
    val updateTarget = io.update.map(_.target)
    val updateValid = io.update.map(_.valid)

    if(wayNum > 1) {
        val fifoPtr = RegInit(VecInit(
            Seq.fill(bankNum)(
                VecInit(Seq.fill(numRows)(0.U(log2Ceil(wayNum).W)))
            )
        ))

        for(j <- 0 until bankNum) {
            val updateBankId = updatePC(j)(bankBits + offsetBits - 1, offsetBits)
            val updateRowId = updatePC(j)(indexBits + offsetBits - 1, offsetBits + bankBits)
            val updateTag = updatePC(j)(tagBits + indexBits + offsetBits - 1, offsetBits + indexBits)

            val waySel = fifoPtr(j)(updateRowId)
            for(i <- 0 until wayNum) {
                when(i.U === waySel && updateValid(j)) {
                    assert(updateBankId === j.U,
                        "l1 btb update pc: 0x%x, (%d) targets bank %d, expected bank %d", updatePC(j), j.U, updateBankId, j.U)
                    val updateItem = WireInit(0.U.asTypeOf((new L1BTBEntry)))
                    updateItem.tag := updateTag
                    updateItem.isCall := updateIsCall(j)
                    updateItem.isReturn := updateIsReturn(j)
                    updateItem.valid := true.B
                    updateItem.target := updateTarget(j)
                    l1_btb_mem(j)(i).write(updateRowId, ~(0.U)(updateItem.getWidth.W), updateItem.asUInt)
                    // l1_btb_tag_array(j)(i).write(updateRowId, updateTag)
                    // l1_btb_prop_array(j)(i).write(updateRowId, Cat(1.U(1.W), updateIsCall(j), updateIsReturn(j)))
                    // l1_btb_target_array(j)(i).write(updateRowId, updateTarget(j))
                    fifoPtr(j)(updateRowId) := waySel + 1.U
                    // printf(p"l1 btb bank[$j($updateBankId)] way[$i] pc[${updatePC(j)}] update! index: $updateRowId \n")
                }
            }
        }
    } else {
        for(j <- 0 until p(FETCH_WIDTH)) {
            val updateBankId = updatePC(j)(bankBits + offsetBits - 1, offsetBits)
            val updateRowId = updatePC(j)(indexBits + offsetBits - 1, offsetBits + bankBits)
            val updateTag = updatePC(j)(tagBits + indexBits + offsetBits - 1, offsetBits + indexBits)
            assert(updateBankId === j.U,
                "l1 btb update(%d) targets bank %d, expected bank %d", j.U, updateBankId, j.U)
            when(updateValid(j)) {
                val updateItem = WireInit(0.U.asTypeOf((new L1BTBEntry)))
                updateItem.tag := updateTag
                updateItem.isCall := updateIsCall(j)
                updateItem.isReturn := updateIsReturn(j)
                updateItem.valid := true.B
                updateItem.target := updateTarget(j)
                l1_btb_mem(j)(0).write(updateRowId, ~(0.U)(updateItem.getWidth.W), updateItem.asUInt)
            }
        }
    }
}   