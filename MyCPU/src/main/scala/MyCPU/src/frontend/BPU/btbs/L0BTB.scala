package MyCPU.frontend.BPU

import chisel3._
import chisel3.util._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

import org.chipsalliance.cde.config._

// Full associativity, update when L1 BTB hit but L0 BTB miss
// L0 BTB is accessed in if0 stage, if hit generate target, if miss generate pc + 4
// return first hit entry, if no hit, return invalid entry with target = pc + 4
// tag,valid, target, isCall, isReturn arrays splited, update by index, hit by tag match and valid bit, 
// good for timing, but may cause more area overhead than combined entry array
// >3GHz on asic
class L0BTB(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val if1_stall = Input(Bool())
        val if1_flush = Input(Bool())
        val rasTopPtr = Input(UInt(log2Ceil(p(RASParam).RASSize + 1).W))
        val rasTop = Input(UInt(p(ADDR_WIDTH).W))
        val req = Input(Vec(p(FETCH_WIDTH), new L0BTBReqInfo))
        val resp = Output(new L0BTBRespInfo)
        val update = Input(new L0BTBUpdateInfo) // only 1 update from L1 BTB, save area and timing, but may cause more mispredictions than multiple updates in one cycle
        val if1_l0_btb_hit = Output(Vec(p(FETCH_WIDTH), Bool()))
    })

    var l0_btb_target_array = RegInit(VecInit.fill(p(L0BTBParam).btbEntryNum)(0.U(p(ADDR_WIDTH).W)))
    val l0_btb_tag_array = RegInit(VecInit.fill(p(L0BTBParam).btbEntryNum)(0.U(p(L0BTBParam).btbTagBits.W)))
    val l0_btb_valid_array = RegInit(VecInit.fill(p(L0BTBParam).btbEntryNum)(false.B))
    val l0_btb_isCall_array = RegInit(VecInit.fill(p(L0BTBParam).btbEntryNum)(false.B))
    val l0_btb_isReturn_array = RegInit(VecInit.fill(p(L0BTBParam).btbEntryNum)(false.B))

    var l0_hit_array = VecInit.fill(p(FETCH_WIDTH))(false.B)

    val l0_btbIndexBits = p(L0BTBParam).btbIndexBits
    val l0_btbTagBits = p(L0BTBParam).btbTagBits
    val l0_btbOffsetBits = p(L0BTBParam).btbOffsetBits

    for(i <- 0 until p(FETCH_WIDTH)) {
        l0_hit_array(i) := io.req(i).valid && 
            l0_btb_valid_array(io.req(i).pc(l0_btbIndexBits + l0_btbOffsetBits - 1, l0_btbOffsetBits)) && 
            l0_btb_tag_array(io.req(i).pc(l0_btbIndexBits + l0_btbOffsetBits - 1, l0_btbOffsetBits)) === 
                io.req(i).pc(p(ADDR_WIDTH)-1, l0_btbIndexBits + l0_btbOffsetBits)
    }

    when(io.update.valid) {
        l0_btb_isCall_array(io.update.pc(l0_btbIndexBits + l0_btbOffsetBits - 1, l0_btbOffsetBits)) := io.update.isCall
        l0_btb_isReturn_array(io.update.pc(l0_btbIndexBits + l0_btbOffsetBits - 1, l0_btbOffsetBits)) := io.update.isReturn
        l0_btb_target_array(io.update.pc(l0_btbIndexBits + l0_btbOffsetBits - 1, l0_btbOffsetBits)) := io.update.target
        l0_btb_tag_array(io.update.pc(l0_btbIndexBits + l0_btbOffsetBits - 1, l0_btbOffsetBits)) := io.update.pc(p(ADDR_WIDTH)-1, l0_btbIndexBits + l0_btbOffsetBits)
        l0_btb_valid_array(io.update.pc(l0_btbIndexBits + l0_btbOffsetBits - 1, l0_btbOffsetBits)) := true.B
    }

    val if1_l0_hit_array = PipeReg(l0_hit_array, io.if1_stall, io.if1_flush)
    io.if1_l0_btb_hit := if1_l0_hit_array
    
    val if1_req = PipeReg(io.req, io.if1_stall, io.if1_flush)
    val if1_rasTopPtr = PipeReg(io.rasTopPtr, io.if1_stall, io.if1_flush)

    val hit_index = PriorityEncoder(if1_l0_hit_array)

    io.resp.valid := if1_l0_hit_array.reduce(_ || _)
    io.resp.target := Mux(
        io.resp.valid, 
        Mux(
            l0_btb_isReturn_array(if1_req(hit_index).pc(l0_btbIndexBits + l0_btbOffsetBits - 1, l0_btbOffsetBits)),
            io.rasTop,
            l0_btb_target_array(if1_req(hit_index).pc(l0_btbIndexBits + l0_btbOffsetBits - 1, l0_btbOffsetBits))
        ),
        if1_req(p(FETCH_WIDTH) - 1).pc + 4.U
    )
    io.resp.isCall := Mux(io.resp.valid, l0_btb_isCall_array(if1_req(hit_index).pc(l0_btbIndexBits + l0_btbOffsetBits - 1, l0_btbOffsetBits)), false.B)
    io.resp.isReturn := Mux(io.resp.valid, l0_btb_isReturn_array(if1_req(hit_index).pc(l0_btbIndexBits + l0_btbOffsetBits - 1, l0_btbOffsetBits)), false.B)
    io.resp.hitIndex := hit_index
    io.resp.rasTopPtr := if1_rasTopPtr
    io.resp.pc := if1_req(hit_index).pc
}