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

        val ex_stall = Output(Bool())
        val result = Output(new ALUResult)
        val uop = Output(new ALUIQEntry)        
    })

    val integerUnit = Module(new IntegerUnit)
    val multUnit = Module(new MultUnit)

    val result = WireInit(0.U(p(DATA_WIDTH).W))
    val brTarget = WireInit(0.U(p(ADDR_WIDTH).W))
    val brJump = WireInit(false.B)

    val isMult = WireInit(false.B)
    val isDiv = WireInit(false.B)
    val isRem = WireInit(false.B)
    val isBranch = WireInit(false.B)
    val isUType = WireInit(false.B)
    val isJAL = WireInit(false.B)

    val rs2useImm = WireInit(false.B)

    isMult := 
        io.alu_item.uop.opcode === OpcodeVal.R &&
        io.alu_item.uop.funct7 === Funct7Val.MUL &&
        !io.alu_item.uop.funct3(2)

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
    
    isBranch := io.alu_item.uop.opcode === OpcodeVal.BEQ
    isUType := 
        io.alu_item.uop.opcode === OpcodeVal.LUI ||
        io.alu_item.uop.opcode === OpcodeVal.AUIPC

    isJAL := io.alu_item.uop.opcode === OpcodeVal.JAL

    rs2useImm := !(
        io.alu_item.uop.opcode === OpcodeVal.R || 
        io.alu_item.uop.opcode === OpcodeVal.BEQ
    ) || isUType
        
    integerUnit.io.valid := 
        !isMult && !isDiv && !isRem && !isBranch 
    
    integerUnit.io.data1 := Mux(isUType, io.alu_item.uop.pc, io.alu_item.ps1_data)
    integerUnit.io.data2 := Mux(rs2useImm, io.alu_item.uop.imm, io.alu_item.ps2_data)
    integerUnit.io.funct3 := io.alu_item.uop.funct3
    integerUnit.io.funct7 := io.alu_item.uop.funct7

    switch(io.alu_item.uop.funct3) {
        is(Funct3Val.BEQ) {
            brJump := io.alu_item.ps1_data === io.alu_item.ps2_data
        }
        is(Funct3Val.BNE) {
            brJump := io.alu_item.ps1_data =/= io.alu_item.ps2_data            
        }
        is(Funct3Val.BLT) {
            brJump := io.alu_item.ps1_data.asSInt < io.alu_item.ps2_data.asSInt             
        }
        is(Funct3Val.BGE) {
            brJump := !(io.alu_item.ps1_data.asSInt < io.alu_item.ps2_data.asSInt)
        }
        is(Funct3Val.BLTU) {
            brJump := io.alu_item.ps1_data < io.alu_item.ps2_data
        }
        is(Funct3Val.BGEU) {
            brJump := !(io.alu_item.ps1_data < io.alu_item.ps2_data)
        }
    }

    brTarget := Mux(
        brJump || isJAL, 
        io.alu_item.uop.pc + io.alu_item.uop.imm, 
        io.alu_item.uop.pc + 4.U
    )

    io.result.HasRd := io.alu_item.uop.HasRd
    io.result.pd := io.alu_item.uop.pd
    io.result.rd := io.alu_item.uop.rd
    io.result.valid := io.alu_item.uop.valid
    io.result.result := 0.U

    io.uop := io.alu_item.uop
    io.uop.branchPredInfo.target := Mux(
        isBranch || isJAL, 
        brTarget,
        io.alu_item.uop.branchPredInfo.target
    )

    switch(Cat(isMult, isDiv, isRem, isJAL)) {
        is("b0000".U) {
            io.result.result := integerUnit.io.result
        }
        is("b1000".U) {

        }
        is("b0100".U) {

        }
        is("b0010".U) {

        }
        // JAL store pc + 4 to rd
        is("b0001".U) {
            io.result.result := io.alu_item.uop.pc + 4.U
        }
    }
}