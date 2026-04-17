package MyCPU.middleend

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._

import org.chipsalliance.cde.config._
import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

// 2 stages dispatch
// to do, judge spec exec load and non spec exec load
// spec exec load may be replayed due to MDP Error, may set spec state for issue items
// no spec exec load is safe, though mdp has pred error
class DispatchStage(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val flush = Input(Bool())
        val stall_from_backpipe = Input(Bool())
        val renameRes_i = Input(Vec(p(FETCH_WIDTH), new uOp))
        val dispatch1_canSpecExec = Input(Vec(p(FETCH_WIDTH), Bool()))
        // to issue enq stage
        val mem_uops = Output(Vec(p(FETCH_WIDTH), new uOp))
        val alu_uops = Output(Vec(p(FETCH_WIDTH), new uOp))
        // val sys_uops = Output(Vec(p(FETCH_WIDTH), new uOp))
    })

    val dispatch1_renameRes = PipeReg(io.renameRes_i, io.stall_from_backpipe, io.flush)
    val dispatch1_renameRes_o = WireInit(VecInit(
        Seq.fill(p(FETCH_WIDTH))(0.U.asTypeOf(new uOp))
    ))

    val dispatch1_isMEMInstMask = WireInit(VecInit(
        Seq.fill(p(FETCH_WIDTH))(false.B)
    ))

    val dispatch1_isALUInstMask = WireInit(VecInit(
        Seq.fill(p(FETCH_WIDTH))(false.B)
    ))

    val dispatch1_isMEMInstPrefixSums = WireInit(VecInit(
        Seq.fill(p(FETCH_WIDTH))(0.U(log2Ceil(p(FETCH_WIDTH)).W))
    ))

    val dispatch1_isALUInstPrefixSums = WireInit(VecInit(
        Seq.fill(p(FETCH_WIDTH))(0.U(log2Ceil(p(FETCH_WIDTH)).W))
    ))

    for(i <- 0 until p(FETCH_WIDTH)) {
        dispatch1_isMEMInstMask(i) := 
            dispatch1_renameRes(i).opcode === OpcodeVal.SB ||
            dispatch1_renameRes(i).opcode === OpcodeVal.LB

        dispatch1_isALUInstMask(i) := ~((dispatch1_isMEMInstMask(i))) // or else
        
    }

    dispatch1_isMEMInstPrefixSums(0) := 0.U
    dispatch1_isALUInstPrefixSums(0) := 0.U

    for(i <- 1 until p(FETCH_WIDTH)) {
        dispatch1_isMEMInstPrefixSums(i) := dispatch1_isMEMInstPrefixSums(i-1) + dispatch1_isMEMInstMask(i)
        dispatch1_isALUInstPrefixSums(i) := dispatch1_isALUInstPrefixSums(i-1) + dispatch1_isALUInstMask(i)
    }

    dispatch1_renameRes_o := dispatch1_renameRes
    for(i <- 0 until p(FETCH_WIDTH)) {
        dispatch1_renameRes_o(i).LDSpecExec := io.dispatch1_canSpecExec(i)
    }

    val dispatch2_isMEMInstPrefixSums = PipeReg(dispatch1_isMEMInstPrefixSums, io.stall_from_backpipe, io.flush)
    val dispatch2_isALUInstPrefixSums = PipeReg(dispatch1_isALUInstPrefixSums, io.stall_from_backpipe, io.flush)
    val dispatch2_renameRes = PipeReg(dispatch1_renameRes_o, io.stall_from_backpipe, io.flush)

    for(b <- 0 until p(FETCH_WIDTH)) {
        // find the first hit
        val alu_hit_mask = VecInit((0 until p(FETCH_WIDTH)).map(i => {
            val isMem = dispatch2_renameRes(i).opcode === OpcodeVal.SB || dispatch2_renameRes(i).opcode === OpcodeVal.LB
            (b.U === dispatch2_isALUInstPrefixSums(i)) && ~(isMem)
        }))

        val mem_hit_mask = VecInit((0 until p(FETCH_WIDTH)).map(i => {
            val isMem = dispatch2_renameRes(i).opcode === OpcodeVal.SB || dispatch2_renameRes(i).opcode === OpcodeVal.LB
            (b.U === dispatch2_isALUInstPrefixSums(i)) && isMem
        }))

        val alu_idx = PriorityEncoder(alu_hit_mask)
        val alu_hit = alu_hit_mask.asUInt.orR

        val mem_idx = PriorityEncoder(mem_hit_mask)
        val mem_hit = mem_hit_mask.asUInt.orR

        when(alu_hit) {
            io.alu_uops(b) := dispatch2_renameRes(alu_idx)
        }.otherwise {
            io.alu_uops(b) := 0.U.asTypeOf(new uOp)
        }

        when(mem_hit) {
            io.mem_uops(b) := dispatch2_renameRes(mem_idx)
        }.otherwise {
            io.mem_uops(b) := 0.U.asTypeOf(new uOp)
        }
    }
}