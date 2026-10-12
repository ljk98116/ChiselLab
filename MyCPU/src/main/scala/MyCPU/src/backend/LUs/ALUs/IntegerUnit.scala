package MyCPU.backend

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

class IntegerUnit(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val integer_input_info = Input(new IntegerUnitInputInfo)
        val result = Output(UInt(p(DATA_WIDTH).W))
    })

    val sign_bits = WireInit(0.U(2.W))
    val less_than = WireInit(false.B)

    sign_bits := Cat(
        io.integer_input_info.data1(p(DATA_WIDTH)-1), 
        io.integer_input_info.data2(p(DATA_WIDTH)-1)
    )

    val op1 = io.integer_input_info.data1
    val op2 = Mux(io.integer_input_info.is_i_type, io.integer_input_info.imm, io.integer_input_info.data2)

    switch(sign_bits) {
        is("b00".U, "b11".U) {
            less_than := io.integer_input_info.data1(p(DATA_WIDTH) - 2, 0) < 
                io.integer_input_info.data2(p(DATA_WIDTH) - 2, 0)
        }
        is("b01".U) {
            less_than := false.B
        }
        is("b10".U) {
            less_than := true.B
        }
    }

    val result = WireInit(0.U(p(DATA_WIDTH).W))
    result := op2

    switch(io.integer_input_info.funct3) {
        is(Funct3Val.ADD) {
            switch(io.integer_input_info.funct7) {
                is(Funct7Val.ADD) {
                    result := io.integer_input_info.data1 + io.integer_input_info.data2
                }
                is(Funct7Val.SUB) {
                    result := io.integer_input_info.data1 - io.integer_input_info.data2
                }
            }
        }

        is(Funct3Val.SLL) {
            result := io.integer_input_info.data1 << io.integer_input_info.data2(4, 0)
        }

        is(Funct3Val.SRL) {
            switch(io.integer_input_info.funct7) {
                is(Funct7Val.SRL) {
                    result := io.integer_input_info.data1 >> io.integer_input_info.data2(4, 0)
                }
                is(Funct7Val.SRA) {
                    result := Mux(
                        io.integer_input_info.data1(31), 
                        (io.integer_input_info.data1 >> io.integer_input_info.data2(4, 0)) & 
                        ~(0.U)(p(DATA_WIDTH).W) , 
                        io.integer_input_info.data1 >> io.integer_input_info.data2(4, 0))
                }
            }
        }

        is(Funct3Val.SLT) {
            result := Mux(less_than, 1.U, 0.U)
        }

        is(Funct3Val.SLTU) {
            result := Mux(io.integer_input_info.data1 < io.integer_input_info.data2, 1.U, 0.U)
        }

        is(Funct3Val.XOR) {
            result := io.integer_input_info.data1 ^ io.integer_input_info.data2
        }

        is(Funct3Val.OR) {
            result := io.integer_input_info.data1 | io.integer_input_info.data2
        }

        is(Funct3Val.AND) {
            result := io.integer_input_info.data1 & io.integer_input_info.data2
        }
    }

    when(io.integer_input_info.is_auipc) {
        result := io.integer_input_info.imm
    }
    
    io.result := Mux(io.integer_input_info.valid, result, 0.U)
}