package MyCPU.frontend.BPU

import chisel3._
import chisel3.util._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._
import org.chipsalliance.cde.config._

// update when commit
// if0 stage access 
// if1 stage judge hit/miss by tag, if hit generate target, if miss generate pc + 4
// if l0 btb miss but l1 btb hit, use l1 btb target to update l0 btb and jump to l1 btb target
// split tag, valid, target, isCall, isReturn arrays, update by index, hit by tag match and valid bit, 
// good for timing, but may cause more area overhead than combined entry array
// use L1 BTB WbQueue to update L0 BTB
// random select victim way
class L1BTB(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val req = Input(Vec(p(FETCH_WIDTH), new L1BTBReqInfo))
        val resp = Output(new L1BTBRespInfo)
        val update = Input(new L1BTBUpdateInfo)
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
    
    require(bankNum == p(FETCH_WIDTH), "bankNum must equal to fetch width")

    val bankBits = log2Ceil(bankNum)

    val l1_btb_tag_array = Seq.fill(bankNum)(
        Seq.fill(wayNum)(SyncReadMem(EntryNum / bankNum / wayNum, UInt(tagBits.W)))
    )
    val l1_btb_prop_array = Seq.fill(bankNum)(Seq.fill(wayNum)(
        SyncReadMem(EntryNum / bankNum / wayNum, UInt(3.W)) // valid, isCall, isReturn
    ))
    val l1_btb_target_array = Seq.fill(bankNum)(Seq.fill(wayNum)(SyncReadMem(EntryNum / bankNum / wayNum, UInt(p(ADDR_WIDTH).W))))

    val reqValid = RegInit(VecInit(
        Seq.fill(p(FETCH_WIDTH))(false.B)
    ))
    val reqPC = RegInit(VecInit(
        Seq.fill(p(FETCH_WIDTH))(0.U(p(ADDR_WIDTH).W))
    ))
    val reqL0Hit = RegInit(VecInit(
        Seq.fill(p(FETCH_WIDTH))(false.B)
    ))

    // judge if read hit the staged update
    val RAWMask = RegInit(VecInit(Seq.fill(bankNum)(false.B)))

    reqValid := io.req.map(_.valid)
    reqPC := io.req.map(_.pc)
    reqL0Hit := io.req.map(_.l0_hit)

    // for 4 way
    // b2 b1 b0
    //        b0
    //   b1        b2
    // way0 way1 way2 way3 
    // ? 0 0 left older, choose way0[b0 = 0, b1 = 0] => hit way0, update LRU to ? 1 1
    // ? 1 0 left older, choose way1[b0 = 0, b1 = 1] => hit way1, update LRU to ? 0 1
    // 0 ? 1 right older, choose way2[b0 = 1, b1 = 0] => hit way2, update LRU to 1 ? 0
    // 1 ? 1 right older, choose way3[b0 = 1, b1 = 1] => hit way3, update LRU to 0 ? 0

    // for 2 way
    // b0
    // 0 left older, choose way0[b0 = 0]
    // 1 right older, choose way1[b0 = 1]

    val hitVec = WireInit(VecInit(
        Seq.fill(bankNum)(
            VecInit(Seq.fill(wayNum)(false.B))
        )
    ))

    val isCallVec = WireInit(VecInit(
        Seq.fill(bankNum)(
            VecInit(Seq.fill(wayNum)(false.B))
        )
    ))

    val isReturnVec = WireInit(VecInit(
        Seq.fill(bankNum)(
            VecInit(Seq.fill(wayNum)(false.B))
        )
    ))

    val validVec = WireInit(VecInit(
        Seq.fill(bankNum)(
            VecInit(Seq.fill(wayNum)(false.B))
        )
    ))

    val updateValid = RegInit(false.B)
    val updatePC = RegInit(0.U(p(ADDR_WIDTH).W))
    val updateIsCall = RegInit(false.B)
    val updateIsReturn = RegInit(false.B)
    val updateTarget = RegInit(0.U(p(ADDR_WIDTH).W))

    // read at if0 stage, use io.req
    for(j <- 0 until p(FETCH_WIDTH)) {
        RAWMask(j) := io.req(j).pc === io.update.pc && io.update.valid && io.req(j).valid
        for(i <- 0 until wayNum) {
            val tag = l1_btb_tag_array(j)(i).read(io.req(j).pc(indexBits + offsetBits - 1, offsetBits + bankBits))
            val prop = l1_btb_prop_array(j)(i).read(io.req(j).pc(indexBits + offsetBits - 1, offsetBits + bankBits))
            val valid = prop(2)
            val isCall = prop(1)
            val isReturn = prop(0)

            // hit judge at if1 stage, use staged req
            hitVec(j)(i) := (reqValid(j) && valid && tag === reqPC(j)(p(ADDR_WIDTH)-1, indexBits + offsetBits + bankBits)) || RAWMask(j) 
            validVec(j)(i) := Mux(RAWMask(j), true.B, valid)
            isCallVec(j)(i) := Mux(RAWMask(j), updateIsCall, isCall)
            isReturnVec(j)(i) := Mux(RAWMask(j), updateIsReturn, isReturn)
        }
    }

    val targetVec = WireInit(VecInit(
        Seq.fill(bankNum)(VecInit(
            Seq.fill(wayNum)(0.U(p(ADDR_WIDTH).W))
        ))
    ))

    for(j <- 0 until bankNum) {
        for(i <- 0 until wayNum) {
            targetVec(j)(i) := l1_btb_target_array(j)(i).read(io.req(j).pc(indexBits + offsetBits - 1, offsetBits + bankBits))
        }
    }

    // double PE may be a critical timing path
    val hitWayArray = WireInit(VecInit(Seq.tabulate(bankNum)(i => PriorityEncoder(hitVec(i)))))
    val hitArray = Seq.tabulate(bankNum)(i => hitVec(i).reduce(_ || _))
    val hitIndex = PriorityEncoder(hitArray)

    io.resp.valid := hitArray.reduce(_ || _)
    io.resp.target := Mux(io.resp.valid, targetVec(hitIndex)(hitWayArray(hitIndex)), io.req(0).pc + (p(FETCH_WIDTH) << 2).U)
    io.resp.isCall := Mux(io.resp.valid, isCallVec(hitIndex)(hitWayArray(hitIndex)), false.B)
    io.resp.isReturn := Mux(io.resp.valid, isReturnVec(hitIndex)(hitWayArray(hitIndex)), false.B)
    
    updateValid := io.update.valid
    updatePC := io.update.pc
    updateIsCall := io.update.isCall
    updateIsReturn := io.update.isReturn
    updateTarget := io.update.target

    val lfsr = chisel3.util.random.LFSR(4)
    val victimWay = WireInit(0.U(log2Ceil(wayNum).W))
    victimWay := lfsr(log2Ceil(wayNum) - 1, 0)

    // same update logic for hit and update
    val updateBankId = io.update.pc(bankBits + offsetBits - 1, offsetBits)
    for(j <- 0 until bankNum) {
        for(i <- 0 until wayNum) {
            when(i.U === victimWay && j.U === updateBankId) {
                when(updateValid) {
                    l1_btb_tag_array(j)(i).write(io.update.pc(p(ADDR_WIDTH)-1, indexBits + offsetBits), io.update.pc(p(ADDR_WIDTH)-1, indexBits + offsetBits))
                    l1_btb_prop_array(j)(i).write(io.update.pc(p(ADDR_WIDTH)-1, indexBits + offsetBits), Cat(1.U(1.W), io.update.isCall, io.update.isReturn))
                    l1_btb_target_array(j)(i).write(io.update.pc(indexBits + offsetBits - 1, offsetBits + bankBits), io.update.target)
                }
            }
        }
    }
}   