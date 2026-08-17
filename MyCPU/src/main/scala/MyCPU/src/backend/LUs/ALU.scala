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
        val alu_item = Input(new ALUInputInfo)

        val integer_input = Input(new IntegerUnitInputInfo)
        val branch_input = Input(new BranchUnitInputInfo)

        val result = Output(new ALUResult)       
    })

    val integerUnit = Module(new IntegerUnit)
    val branchUnit = Module(new BranchUnit)

    val result = WireInit(0.U(p(DATA_WIDTH).W))
    val brTarget = WireInit(0.U(p(ADDR_WIDTH).W))
    val brJump = WireInit(false.B)

    integerUnit.io.integer_input_info := io.integer_input
    branchUnit.io.branch_inputs := io.branch_input

    brTarget := branchUnit.io.br_target
    brJump := branchUnit.io.br_jump
    
    val resultIdx = WireInit(0.U(1.W))
    val results = WireInit(VecInit(
        Seq.fill(2)(0.U(p(DATA_WIDTH).W))
    ))

    results(0) := integerUnit.io.result
    results(1) := branchUnit.io.result

    val sel = Cat(
        io.branch_input.valid,
        io.integer_input.valid
    )

    result := Mux1H(
        Seq(
            sel(0) -> results(0),
            sel(1) -> results(1),
        )
    )
    
    io.result.branchPredInfo := io.alu_item.branchPredInfo
    io.result.branchPredInfo.target := Mux(io.branch_input.valid || io.branch_input.is_jal, brTarget, 0.U)

    io.result.valid := io.alu_item.valid
    io.result.HasRd := io.alu_item.HasRd
    io.result.result := result
    io.result.pd := io.alu_item.pd
    io.result.robLoc := io.alu_item.robLoc
}