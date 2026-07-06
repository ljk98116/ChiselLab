package MyCPU.backend

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

class BranchUnit(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val branch_inputs = Input(new BranchUnitInputInfo)
        val result = Output(UInt(p(DATA_WIDTH).W))    
        val br_jump = Output(Bool())
        val br_target = Output(UInt(p(ADDR_WIDTH).W))            
    })

    val br_jump = WireInit(false.B)
    val br_target = WireInit(0.U(p(DATA_WIDTH).W))

    switch(io.branch_inputs.funct3) {
        is(Funct3Val.BEQ) {
            br_jump := io.branch_inputs.data1 === io.branch_inputs.data2
        }
        is(Funct3Val.BNE) {
            br_jump := io.branch_inputs.data1 =/= io.branch_inputs.data2            
        }
        is(Funct3Val.BLT) {
            br_jump := io.branch_inputs.data1.asSInt < io.branch_inputs.data2.asSInt             
        }
        is(Funct3Val.BGE) {
            br_jump := !(io.branch_inputs.data1.asSInt < io.branch_inputs.data2.asSInt)
        }
        is(Funct3Val.BLTU) {
            br_jump := io.branch_inputs.data1 < io.branch_inputs.data2
        }
        is(Funct3Val.BGEU) {
            br_jump := !(io.branch_inputs.data1 < io.branch_inputs.data2)
        }
    }

    br_target := Mux(
        br_jump || io.branch_inputs.is_jal, 
        io.branch_inputs.pc + io.branch_inputs.imm, 
        io.branch_inputs.pc + 4.U
    )

    io.br_jump := br_jump
    io.br_target := br_target    
    io.result := Mux(io.branch_inputs.is_jal, io.branch_inputs.pc + 4.U, 0.U)
}
