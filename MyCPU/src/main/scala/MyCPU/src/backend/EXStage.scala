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

        val integer_inputs = Input(Vec(p(ALU_NUM), new IntegerUnitInputInfo))
        val branch_inputs = Input(Vec(p(ALU_NUM), new BranchUnitInputInfo))
        val mult_inputs = Input(Vec(p(MULT_NUM), new MultUnitInputInfo))
        val div_inputs = Input(Vec(p(DIV_NUM), new DivUnitInputInfo))

        val alu_items = Input(Vec(p(ALU_NUM), new ALUInputInfo))
        val mult_items = Input(Vec(p(MULT_NUM), new ALUInputInfo))
        val div_items = Input(Vec(p(DIV_NUM), new ALUInputInfo))

        val ldu_items = Input(Vec(p(LDU_NUM), new LDUInputInfo))
        val stu_items = Input(Vec(p(STU_NUM), new STUInputInfo))
        val sys_items = Input(Vec(p(SYS_ALU_NUM), new SYSEXUInputInfo))

        val aluResult = Output(Vec(p(ALU_NUM), new ALUResult))
        val lduResult = Output(Vec(p(LDU_NUM), new AGUResult))
        val stuResult = Output(Vec(p(STU_NUM), new AGUResult))
        val sysResult = Output(Vec(p(SYS_ALU_NUM), new SysResult))
        val multResult = Output(Vec(p(MULT_NUM), new ALUResult))
        val divResult = Output(Vec(p(DIV_NUM), new ALUResult))

        val multReadyVec = Output(Vec(p(MULT_NUM), Bool()))
        val divReadyVec = Output(Vec(p(DIV_NUM), Bool()))
    })

    val alus = Seq.fill(p(ALU_NUM))(Module(new ALU))
    val ldus = Seq.fill(p(LDU_NUM))(Module(new LDU))
    val stus = Seq.fill(p(STU_NUM))(Module(new STU))
    val mult_units = Seq.fill(p(MULT_NUM))(Module(new MultUnit))
    val div_units = Seq.fill(p(DIV_NUM))(Module(new DivUnit))
    val sys_exus = Seq.fill(p(SYS_ALU_NUM))(Module(new SYSEXU))

    val alu_items = PipeReg(io.alu_items, io.stall_from_backpipe, io.flush)
    val mult_items = PipeReg(io.mult_items, io.stall_from_backpipe, io.flush)
    val div_items = PipeReg(io.div_items, io.stall_from_backpipe, io.flush)

    val ldu_items = PipeReg(io.ldu_items, io.stall_from_backpipe, io.flush)
    val stu_items = PipeReg(io.stu_items, io.stall_from_backpipe, io.flush)
    val sys_items = PipeReg(io.sys_items, io.stall_from_backpipe, io.flush)

    val integer_inputs = PipeReg(io.integer_inputs, io.stall_from_backpipe, io.flush)
    val branch_inputs = PipeReg(io.branch_inputs, io.stall_from_backpipe, io.flush)
    val mult_inputs = PipeReg(io.mult_inputs, io.stall_from_backpipe, io.flush)
    val div_inputs = PipeReg(io.div_inputs, io.stall_from_backpipe, io.flush)

    for(b <- 0 until p(ALU_NUM)) {
        alus(b).io.alu_item := alu_items(b)
        alus(b).io.flush := io.flush
        alus(b).io.stall_from_backpipe := io.stall_from_backpipe

        alus(b).io.integer_input := integer_inputs(b)
        alus(b).io.branch_input := branch_inputs(b)

        io.aluResult(b) := alus(b).io.result
    }

    for(b <- 0 until p(MULT_NUM)) {
        mult_units(b).io.flush := io.flush
        mult_units(b).io.stall_from_backpipe := io.stall_from_backpipe
        mult_units(b).io.mult_input_info := mult_inputs(b)

        io.multReadyVec(b) := mult_units(b).io.multReady

        io.multResult(b).HasRd := mult_items(b).HasRd
        io.multResult(b).branchPredInfo := mult_items(b).branchPredInfo
        io.multResult(b).pd := mult_items(b).pd
        io.multResult(b).result := mult_units(b).io.result
        io.multResult(b).robLoc := mult_items(b).robLoc
        io.multResult(b).valid := mult_items(b).valid
    }

    for(b <- 0 until p(DIV_NUM)) {
        val isREM = div_inputs(b).funct3(1)

        div_units(b).io.flush := io.flush
        div_units(b).io.stall_from_backpipe := io.stall_from_backpipe
        div_units(b).io.div_input_info := div_inputs(b)

        io.divReadyVec(b) := div_units(b).io.divReady

        io.divResult(b).HasRd := div_items(b).HasRd
        io.divResult(b).branchPredInfo := div_items(b).branchPredInfo
        io.divResult(b).pd := div_items(b).pd
        io.divResult(b).result := Mux(isREM, div_units(b).io.remResult, div_units(b).io.divResult)
        io.divResult(b).robLoc := div_items(b).robLoc
        io.divResult(b).valid := div_items(b).valid
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
}