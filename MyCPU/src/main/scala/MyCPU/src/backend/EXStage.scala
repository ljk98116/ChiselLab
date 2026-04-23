package MyCPU.backend

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

class EXStage(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val flush = Input(Bool())
        val stall_from_backpipe = Input(Bool())

        val alu_items = Input(Vec(p(ALU_NUM), new ALUInputInfo))
        val ldu_items = Input(Vec(p(LDU_NUM), new LDUInputInfo))
        val stu_items = Input(Vec(p(STU_NUM), new STUInputInfo))
        val sys_items = Input(Vec(p(SYS_ALU_NUM), new SYSEXUInputInfo))

        val ex_stall = Output(Bool())
    })

    val alu_items = PipeReg(io.alu_items, io.stall_from_backpipe || io.ex_stall, io.flush)
    val ldu_items = PipeReg(io.ldu_items, io.stall_from_backpipe || io.ex_stall, io.flush)
    val stu_items = PipeReg(io.stu_items, io.stall_from_backpipe || io.ex_stall, io.flush)
    val sys_items = PipeReg(io.sys_items, io.stall_from_backpipe || io.ex_stall, io.flush)

    
}