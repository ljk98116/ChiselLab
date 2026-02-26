package MyCPU.frontend.BPU

import chisel3._
import chisel3.util._

import MyCPU.config._
import MyCPU.io._
import org.chipsalliance.cde.config._

// only push when call, only pop when return, use the final branch result to update ras, because if the final branch is not call/return, it won't change ras, if the final branch is call/return, it will update ras according to the final branch result, no matter what the intermediate branch results are, because the intermediate branch results may be mispredicted, we only care about the final branch result which must be correct after commit
// access & update ras at if1 stage
class RAS(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val next_ras_top_ptr = Output(UInt(log2Ceil(p(RASParam).RASSize).W))
        val next_ras_top = Output(UInt(p(ADDR_WIDTH).W))
        val next_ras_top_valid = Input(Bool())
        // from if0 stage, used for ras update
        val rasTop = Output(UInt(p(ADDR_WIDTH).W))
        val rasTopPtr = Input(UInt(log2Ceil(p(RASParam).RASSize).W))
        val rasTopValid = Output(Bool())
    })

    val ras_stack_depth = p(RASParam).RASSize + 1 // rasTopPtr is 0 means invalid
    
    val ras_stack = RegInit(VecInit.fill(ras_stack_depth)(0.U(p(ADDR_WIDTH).W)))
    val ras_top = RegInit(0.U(log2Ceil(ras_stack_depth).W))

    io.rasTop := ras_stack(ras_top)
    io.rasTopPtr := ras_top
    io.rasTopValid := ras_top < ras_stack_depth.U && ras_top =/= 0.U

    ras_top := Mux(io.next_ras_top_ptr >= ras_stack_depth.U && io.next_ras_top_valid, (ras_stack_depth - 1).U, io.next_ras_top_ptr)
    ras_stack(io.next_ras_top_ptr) := Mux(io.next_ras_top_valid, io.next_ras_top, ras_stack(io.next_ras_top_ptr))
}