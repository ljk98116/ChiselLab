package MyCPU.middleend

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._

import org.chipsalliance.cde.config._
import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._
import java.lang.classfile.Opcode

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
        val load_uops = Output(Vec(p(FETCH_WIDTH), new uOp))
        val store_uops = Output(Vec(p(FETCH_WIDTH), new uOp))
        val alu_uops = Output(Vec(p(FETCH_WIDTH), new uOp))
        val sys_uops = Output(Vec(p(FETCH_WIDTH), new uOp))
    })

    val dispatch1_renameRes = PipeReg(io.renameRes_i, io.stall_from_backpipe, io.flush)
    val dispatch1_renameRes_o = WireInit(VecInit(
        Seq.fill(p(FETCH_WIDTH))(0.U.asTypeOf(new uOp))
    ))

    val dispatch1_isLoadInstMask = WireInit(VecInit(
        Seq.fill(p(FETCH_WIDTH))(false.B)
    ))

    val dispatch1_isStoreInstMask = WireInit(VecInit(
        Seq.fill(p(FETCH_WIDTH))(false.B)
    ))

    val dispatch1_isALUInstMask = WireInit(VecInit(
        Seq.fill(p(FETCH_WIDTH))(false.B)
    ))

    val dispatch1_isSYSInstMask = WireInit(VecInit(
        Seq.fill(p(FETCH_WIDTH))(false.B)
    ))

    val dispatch1_isLoadInstPrefixSums = WireInit(VecInit(
        Seq.fill(p(FETCH_WIDTH))(0.U(log2Ceil(p(FETCH_WIDTH)).W))
    ))

    val dispatch1_isStoreInstPrefixSums = WireInit(VecInit(
        Seq.fill(p(FETCH_WIDTH))(0.U(log2Ceil(p(FETCH_WIDTH)).W))
    ))

    val dispatch1_isALUInstPrefixSums = WireInit(VecInit(
        Seq.fill(p(FETCH_WIDTH))(0.U(log2Ceil(p(FETCH_WIDTH)).W))
    ))

    val dispatch1_isSYSInstPrefixSums = WireInit(VecInit(
        Seq.fill(p(FETCH_WIDTH))(0.U(log2Ceil(p(FETCH_WIDTH)).W))
    ))

    for(i <- 0 until p(FETCH_WIDTH)) {
        dispatch1_isLoadInstMask(i) := 
            dispatch1_renameRes(i).opcode === OpcodeVal.LB

        dispatch1_isStoreInstMask(i) := 
            dispatch1_renameRes(i).opcode === OpcodeVal.SB

        dispatch1_isSYSInstMask(i) :=
            dispatch1_renameRes(i).opcode === OpcodeVal.CSR

        dispatch1_isALUInstMask(i) := 
            ~((dispatch1_isLoadInstMask(i) || dispatch1_isStoreInstMask(i) || dispatch1_isSYSInstMask(i))) // or else
    }

    dispatch1_isLoadInstPrefixSums(0) := 0.U
    dispatch1_isStoreInstPrefixSums(0) := 0.U
    dispatch1_isALUInstPrefixSums(0) := 0.U
    dispatch1_isSYSInstPrefixSums(0) := 0.U

    for(i <- 1 until p(FETCH_WIDTH)) {
        dispatch1_isLoadInstPrefixSums(i) := dispatch1_isLoadInstPrefixSums(i-1) + dispatch1_isLoadInstMask(i)
        dispatch1_isStoreInstPrefixSums(i) := dispatch1_isStoreInstPrefixSums(i-1) + dispatch1_isStoreInstMask(i)
        dispatch1_isALUInstPrefixSums(i) := dispatch1_isALUInstPrefixSums(i-1) + dispatch1_isALUInstMask(i)
        dispatch1_isSYSInstPrefixSums(i) := dispatch1_isSYSInstPrefixSums(i-1) + dispatch1_isSYSInstMask(i)
    }

    dispatch1_renameRes_o := dispatch1_renameRes
    for(i <- 0 until p(FETCH_WIDTH)) {
        dispatch1_renameRes_o(i).LDSpecExec := io.dispatch1_canSpecExec(i)
    }

    val dispatch2_isLoadInstPrefixSums = PipeReg(dispatch1_isLoadInstPrefixSums, io.stall_from_backpipe, io.flush)
    val dispatch2_isStoreInstPrefixSums = PipeReg(dispatch1_isStoreInstPrefixSums, io.stall_from_backpipe, io.flush)
    val dispatch2_isALUInstPrefixSums = PipeReg(dispatch1_isALUInstPrefixSums, io.stall_from_backpipe, io.flush)
    val dispatch2_isSYSInstPrefixSums = PipeReg(dispatch1_isSYSInstPrefixSums, io.stall_from_backpipe, io.flush)
    val dispatch2_renameRes = PipeReg(dispatch1_renameRes_o, io.stall_from_backpipe, io.flush)

    for(b <- 0 until p(FETCH_WIDTH)) {
        // find the first hit
        val alu_hit_mask = VecInit((0 until p(FETCH_WIDTH)).map(i => {
            val isMemOrSys = 
                dispatch2_renameRes(i).opcode === OpcodeVal.SB || 
                dispatch2_renameRes(i).opcode === OpcodeVal.LB ||
                dispatch2_renameRes(i).opcode === OpcodeVal.CSR
            (b.U === dispatch2_isALUInstPrefixSums(i)) && ~(isMemOrSys)
        }))

        val load_hit_mask = VecInit((0 until p(FETCH_WIDTH)).map(i => {
            val isLoad = dispatch2_renameRes(i).opcode === OpcodeVal.LB
            (b.U === dispatch2_isLoadInstPrefixSums(i)) && isLoad
        }))

        val store_hit_mask = VecInit((0 until p(FETCH_WIDTH)).map(i => {
            val isStore = dispatch2_renameRes(i).opcode === OpcodeVal.SB
            (b.U === dispatch2_isStoreInstPrefixSums(i)) && isStore
        }))

        val sys_hit_mask = VecInit((0 until p(FETCH_WIDTH)).map( i => {
            val isSYS = dispatch2_renameRes(i).opcode === OpcodeVal.CSR
            (b.U === dispatch2_isSYSInstPrefixSums(i)) && isSYS            
        }))

        val alu_idx = PriorityEncoder(alu_hit_mask)
        val alu_hit = alu_hit_mask.asUInt.orR

        val load_idx = PriorityEncoder(load_hit_mask)
        val load_hit = load_hit_mask.asUInt.orR

        val store_idx = PriorityEncoder(store_hit_mask)
        val store_hit = store_hit_mask.asUInt.orR

        val sys_idx = PriorityEncoder(sys_hit_mask)
        val sys_hit = sys_hit_mask.asUInt.orR

        when(alu_hit) {
            io.alu_uops(b) := dispatch2_renameRes(alu_idx)
        }.otherwise {
            io.alu_uops(b) := 0.U.asTypeOf(new uOp)
        }

        when(load_hit) {
            io.load_uops(b) := dispatch2_renameRes(load_idx)
        }.otherwise {
            io.load_uops(b) := 0.U.asTypeOf(new uOp)
        }

        when(store_hit) {
            io.store_uops(b) := dispatch2_renameRes(store_idx)
        }.otherwise {
            io.store_uops(b) := 0.U.asTypeOf(new uOp)
        }

        when(sys_hit) {
            io.sys_uops(b) := dispatch2_renameRes(sys_idx)
        }.otherwise {
            io.sys_uops(b) := 0.U.asTypeOf(new uOp)
        }

    }
}