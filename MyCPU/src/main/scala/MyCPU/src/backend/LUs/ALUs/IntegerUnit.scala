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

    val result = WireInit(0.U(p(DATA_WIDTH).W))
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
            result := Mux(io.integer_input_info.data1.asSInt < io.integer_input_info.data2.asSInt, 1.U, 0.U)
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

    io.result := Mux(io.integer_input_info.valid, result, 0.U)
}