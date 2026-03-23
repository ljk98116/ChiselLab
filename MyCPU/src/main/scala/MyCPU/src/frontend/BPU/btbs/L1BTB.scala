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
// random select victim way
// >3GHz on asic, small size synth as DFFs
class L1BTB(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val flush = Input(Bool())
        val if2_stall = Input(Bool())
        val req = Input(Vec(p(FETCH_WIDTH), new L1BTBReqInfo))
        val resp = Output(new L1BTBRespInfo)
        val update = Input(Vec(p(FETCH_WIDTH), new L1BTBUpdateInfo)) // no bank conflict update
        val if2_l1_btb_hit = Output(Vec(p(FETCH_WIDTH), Bool()))
    })

    val wayNum = p(UseFourWayBTB) match {
        case true => 4
        case false => 2
    }

    val EntryNum = p(L1BTBParam).btbEntryNum
    val tagBits = p(L1BTBParam).btbTagBits
    val indexBits = p(L1BTBParam).btbIndexBits
    val offsetBits = p(L1BTBParam).btbOffsetBits
    val bankNum = p(L1BTBParam).btbBankNum
    
    require(bankNum == p(FETCH_WIDTH), "l1 btb bankNum must equal to fetch width")

    val bankBits = log2Ceil(bankNum)

    val l1_btb_tag_array = Seq.fill(bankNum)(
        Seq.fill(wayNum)(SyncReadMem(EntryNum / bankNum / wayNum, UInt(tagBits.W)))
    )

    val l1_btb_prop_array = Seq.fill(bankNum)(Seq.fill(wayNum)(
        SyncReadMem(EntryNum / bankNum / wayNum, UInt(3.W)) // valid, isCall, isReturn
    ))

    val l1_btb_target_array = Seq.fill(bankNum)(Seq.fill(wayNum)(SyncReadMem(EntryNum / bankNum / wayNum, UInt(p(ADDR_WIDTH).W))))
    
    val if1_reqValid = RegInit(VecInit(
        Seq.fill(p(FETCH_WIDTH))(false.B)
    ))

    val if1_reqPC = RegInit(VecInit(
        Seq.fill(p(FETCH_WIDTH))(0.U(p(ADDR_WIDTH).W))
    ))

    if1_reqValid := io.req.map(_.valid)
    if1_reqPC := io.req.map(_.pc)

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

    val updateValid = RegInit(VecInit(Seq.fill(bankNum)(false.B)))
    val updatePC = RegInit(VecInit(Seq.fill(bankNum)(0.U(p(ADDR_WIDTH).W))))
    val updateIsCall = RegInit(VecInit(Seq.fill(bankNum)(false.B)))
    val updateIsReturn = RegInit(VecInit(Seq.fill(bankNum)(false.B)))
    val updateTarget = RegInit(VecInit(Seq.fill(bankNum)(0.U(p(ADDR_WIDTH).W))))

    // read at if0 stage, use io.req
    for(j <- 0 until p(FETCH_WIDTH)) {
        for(i <- 0 until wayNum) {
            val if1_tag = l1_btb_tag_array(j)(i).read(io.req(j).pc(indexBits + offsetBits - 1, offsetBits + bankBits))
            val if1_prop = l1_btb_prop_array(j)(i).read(io.req(j).pc(indexBits + offsetBits - 1, offsetBits + bankBits))

            val valid = if1_prop(2)
            val isCall = if1_prop(1)
            val isReturn = if1_prop(0)

            // hit judge at if1 stage, use staged req
            if1_hitVec(j)(i) := (if1_reqValid(j) && valid && if1_tag === if1_reqPC(j)(p(ADDR_WIDTH)-1, indexBits + offsetBits + bankBits)) 
            if1_validVec(j)(i) := valid
            if1_isCallVec(j)(i) := isCall
            if1_isReturnVec(j)(i) := isReturn
        }
    }

    val if1_targetVec = WireInit(VecInit(
        Seq.fill(bankNum)(VecInit(
            Seq.fill(wayNum)(0.U(p(ADDR_WIDTH).W))
        ))
    ))

    for(j <- 0 until bankNum) {
        for(i <- 0 until wayNum) {
            if1_targetVec(j)(i) := l1_btb_target_array(j)(i).read(io.req(j).pc(indexBits + offsetBits - 1, offsetBits + bankBits))
        }
    }

    val if2_reqPC = PipeReg(if1_reqPC, io.if2_stall, io.flush)
    val if2_reqValid = PipeReg(if1_reqValid, io.if2_stall, io.flush)

    val if2_hitVec = PipeReg(if1_hitVec, io.if2_stall, io.flush)
    val if2_targetVec = PipeReg(if1_targetVec, io.if2_stall, io.flush)
    val if2_isCallVec = PipeReg(if1_isCallVec, io.if2_stall, io.flush)
    val if2_isReturnVec = PipeReg(if1_isReturnVec, io.if2_stall, io.flush)

    // double PE may be a critical timing path
    val if2_hitWayArray = WireInit(VecInit(Seq.tabulate(bankNum)(i => PriorityEncoder(if2_hitVec(i)))))
    val if2_hitArray = Seq.tabulate(bankNum)(i => if2_hitVec(i).reduce(_ || _))
    val if2_hitIndex = PriorityEncoder(if2_hitArray)

    io.resp.valid := if2_hitArray.reduce(_ || _)
    io.resp.target := Mux(io.resp.valid, if2_targetVec(if2_hitIndex)(if2_hitWayArray(if2_hitIndex)), if2_reqPC(0) + (p(FETCH_WIDTH) << 2).U)
    io.resp.isCall := Mux(io.resp.valid, if2_isCallVec(if2_hitIndex)(if2_hitWayArray(if2_hitIndex)), false.B)
    io.resp.isReturn := Mux(io.resp.valid, if2_isReturnVec(if2_hitIndex)(if2_hitWayArray(if2_hitIndex)), false.B)
    io.resp.hitIndex := if2_hitIndex
    io.if2_l1_btb_hit := if2_hitArray
    
    updateValid := io.update.map(_.valid)
    updatePC := io.update.map(_.pc)
    updateIsCall := io.update.map(_.isCall)
    updateIsReturn := io.update.map(_.isReturn)
    updateTarget := io.update.map(_.target)

    val lfsr = chisel3.util.random.LFSR(4)
    val victimWay = WireInit(0.U(log2Ceil(wayNum).W))
    victimWay := lfsr(log2Ceil(wayNum) - 1, 0)

    // same update logic for hit and update
    for(j <- 0 until bankNum) {
        val updateBankId = updatePC(j)(bankBits + offsetBits - 1, offsetBits)
        for(i <- 0 until wayNum) {
            when(i.U === victimWay) {
                when(updateValid(j)) {
                    l1_btb_tag_array(j)(i).write(updatePC(j)(p(ADDR_WIDTH)-1, indexBits + offsetBits), updatePC(j)(p(ADDR_WIDTH)-1, indexBits + offsetBits))
                    l1_btb_prop_array(j)(i).write(updatePC(j)(p(ADDR_WIDTH)-1, indexBits + offsetBits), Cat(1.U(1.W), updateIsCall(j), updateIsReturn(j)))
                    l1_btb_target_array(j)(i).write(updatePC(j)(indexBits + offsetBits - 1, offsetBits + bankBits), updateTarget(j))
                }
            }
        }
    }
}   