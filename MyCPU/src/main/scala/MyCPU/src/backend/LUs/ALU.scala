package MyCPU.backend

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

// not impl JALR, expected exec JALR at decode stage
class ALU(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val flush = Input(Bool())
        val stall_from_backpipe = Input(Bool())
        val alu_item = Input(new ALUInputInfo)

        val integer_input = Input(new IntegerUnitInputInfo)
        val branch_input = Input(new BranchUnitInputInfo)
        val mult_input = Input(new MultUnitInputInfo)
        val div_input = Input(new DivUnitInputInfo)

        val ex_stall = Output(Bool())
        val result = Output(new ALUResult)
        val uop = Output(new ALUIQEntry)        
    })

    val integerUnit = Module(new IntegerUnit)
    val multUnit = Module(new MultUnit)
    val divUnit = Module(new DivUnit)
    val branchUnit = Module(new BranchUnit)

    val result = WireInit(0.U(p(DATA_WIDTH).W))
    val brTarget = WireInit(0.U(p(ADDR_WIDTH).W))
    val brJump = WireInit(false.B)

    integerUnit.io.integer_input_info := io.integer_input
    branchUnit.io.branch_inputs := io.branch_input
    multUnit.io.mult_input_info := io.mult_input
    divUnit.io.div_input_info := io.div_input

    multUnit.io.flush := io.flush
    multUnit.io.stall_from_backpipe := io.stall_from_backpipe

    divUnit.io.flush := io.flush
    divUnit.io.stall_from_backpipe := io.stall_from_backpipe

    brTarget := branchUnit.io.br_target
    brJump := branchUnit.io.br_jump
    
    io.ex_stall := (!multUnit.io.multReady && io.mult_input.valid) || 
        (!divUnit.io.divReady && io.div_input.valid)
    
    val resultIdx = WireInit(0.U(2.W))
    val results = WireInit(VecInit(
        Seq.fill(4)(0.U(p(DATA_WIDTH).W))
    ))
    val isDiv = WireInit(false.B)
    val isRem = WireInit(false.B)

    isDiv := 
        io.alu_item.uop.opcode === OpcodeVal.R &&
        io.alu_item.uop.funct7 === Funct7Val.MUL &&
        io.alu_item.uop.funct3(2) &&
        !io.alu_item.uop.funct3(1)

    isRem := 
        io.alu_item.uop.opcode === OpcodeVal.R &&
        io.alu_item.uop.funct7 === Funct7Val.MUL &&
        io.alu_item.uop.funct3(2) &&
        io.alu_item.uop.funct3(1)

    results(0) := integerUnit.io.result
    results(1) := branchUnit.io.result
    results(2) := multUnit.io.result
    results(3) := Mux(
        isDiv, divUnit.io.divResult, 
        Mux(isRem, divUnit.io.remResult, 0.U)
    )

    val sel = Cat(
        io.div_input.valid,
        io.mult_input.valid,
        io.branch_input.valid,
        io.integer_input.valid
    )

    result := Mux1H(
        Seq(
            sel(0) -> results(0),
            sel(1) -> results(1),
            sel(2) -> results(2),
            sel(3) -> results(3)
        )
    )

    io.uop := Mux(io.alu_item.valid, io.alu_item.uop, 0.U.asTypeOf(new ALUIQEntry))
    io.uop.branchPredInfo.target := Mux(io.branch_input.valid || io.branch_input.is_jal, brTarget, 0.U)

    io.result.valid := io.alu_item.valid
    io.result.HasRd := io.alu_item.uop.HasRd
    io.result.result := result
    io.result.pd := io.alu_item.uop.pd
    io.result.rd := io.alu_item.uop.rd
    io.result.robLoc := io.alu_item.uop.robLoc
}