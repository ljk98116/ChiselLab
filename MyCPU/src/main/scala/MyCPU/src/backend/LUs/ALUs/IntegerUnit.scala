package MyCPU.backend

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

class IntegerUnit(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val valid = Input(Bool())
        val funct3 = Input(UInt(3.W))
        val funct7 = Input(UInt(7.W))
        val data1 = Input(UInt(p(DATA_WIDTH).W))
        val data2 = Input(UInt(p(DATA_WIDTH).W))
        val result = Output(UInt(p(DATA_WIDTH).W))
    })

    val result = WireInit(0.U(p(DATA_WIDTH).W))
    switch(io.funct3) {
        is(Funct3Val.ADD) {
            switch(io.funct7) {
                is(Funct7Val.ADD) {
                    result := io.data1 + io.data2
                }
                is(Funct7Val.SUB) {
                    result := io.data1 - io.data2
                }
            }
        }

        is(Funct3Val.SLL) {
            result := io.data1 << io.data2(4, 0)
        }

        is(Funct3Val.SRL) {
            switch(io.funct7) {
                is(Funct7Val.SRL) {
                    result := io.data1 >> io.data2(4, 0)
                }
                is(Funct7Val.SRA) {
                    result := Mux(io.data1(31), (io.data1 >> io.data2(4, 0)) & ~(0.U)(p(DATA_WIDTH).W) , io.data1 >> io.data2(4, 0))
                }
            }
        }

        is(Funct3Val.SLT) {
            result := Mux(io.data1.asSInt < io.data2.asSInt, 1.U, 0.U)
        }

        is(Funct3Val.SLTU) {
            result := Mux(io.data1 < io.data2, 1.U, 0.U)
        }

        is(Funct3Val.XOR) {
            result := io.data1 ^ io.data2
        }

        is(Funct3Val.OR) {
            result := io.data1 | io.data2
        }

        is(Funct3Val.AND) {
            result := io.data1 & io.data2
        }
    }

    io.result := Mux(io.valid, result, 0.U)
}