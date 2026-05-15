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

        val aluResult = Output(Vec(p(ALU_NUM), new ALUResult))
        val lduResult = Output(Vec(p(LDU_NUM), new AGUResult))
        val stuResult = Output(Vec(p(STU_NUM), new AGUResult))
        val sysResult = Output(Vec(p(SYS_ALU_NUM), new SysResult))
        
        val ex_stall = Output(Bool())
    })

    val alus = Seq.fill(p(ALU_NUM))(Module(new ALU))
    val ldus = Seq.fill(p(LDU_NUM))(Module(new LDU))
    val stus = Seq.fill(p(STU_NUM))(Module(new STU))
    val sys_exus = Seq.fill(p(SYS_ALU_NUM))(Module(new SYSEXU))

    val alu_items = PipeReg(io.alu_items, io.stall_from_backpipe || io.ex_stall, io.flush)
    val ldu_items = PipeReg(io.ldu_items, io.stall_from_backpipe || io.ex_stall, io.flush)
    val stu_items = PipeReg(io.stu_items, io.stall_from_backpipe || io.ex_stall, io.flush)
    val sys_items = PipeReg(io.sys_items, io.stall_from_backpipe || io.ex_stall, io.flush)

    for(b <- 0 until p(ALU_NUM)) {
        alus(b).io.alu_item := alu_items(b)
        alus(b).io.flush := io.flush
        alus(b).io.stall_from_backpipe := io.stall_from_backpipe
        
        io.aluResult(b) := alus(b).io.result
    }

    for(b <- 0 until p(LDU_NUM)) {
        ldus(b).io.flush := io.flush
        ldus(b).io.stall_from_backpipe := io.stall_from_backpipe
        ldus(b).io.ldu_item := ldu_items(b)

        io.lduResult(b) := ldus(b).io.result
    }

    for(b <- 0 until p(STU_NUM)) {
        stus(b).io.flush := io.flush
        stus(b).io.stall_from_backpipe := io.stall_from_backpipe
        stus(b).io.stu_item := stu_items(b)

        io.stuResult(b) := stus(b).io.result
    }    

    for(b <- 0 until p(SYS_ALU_NUM)) {
        sys_exus(b).io.flush := io.flush
        sys_exus(b).io.stall_from_backpipe := io.stall_from_backpipe
        sys_exus(b).io.sys_item := sys_items(b)

        io.sysResult(b) := sys_exus(b).io.result
    }
    
    io.ex_stall := alus.map(m => m.io.ex_stall).reduce(_||_)
}