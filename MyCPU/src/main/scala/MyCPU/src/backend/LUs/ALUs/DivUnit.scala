package MyCPU.backend

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

// get abs value for signed div, use abs value to div
// use abs value to select final result
class DivUnit(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val flush = Input(Bool())
        val stall_from_backpipe = Input(Bool())

        // val valid = Input(Bool())
        // val funct3 = Input(UInt(3.W)) 

        // val data1 = Input(UInt(p(DATA_WIDTH).W))
        // val data2 = Input(UInt(p(DATA_WIDTH).W))
        val div_input_info = Input(new DivUnitInputInfo)
        val divResult = Output(UInt(p(DATA_WIDTH).W))
        val remResult = Output(UInt(p(DATA_WIDTH).W))

        val resValid = Output(Bool()) 

        val divReady = Output(Bool())      
    })

    val isREM = io.div_input_info.funct3(1)
    val isDIV = !io.div_input_info.funct3(1)
    val isSigned = !io.div_input_info.funct3(0)

    val divResultIsMinus = RegInit(false.B)
    val remResultIsMinus = RegInit(false.B)

    // at most 33 periods
    val cycleCounter = RegInit(0.U((log2Ceil(p(DATA_WIDTH) + 1)).W))

    // all bits preset to 1
    val divEnd = RegInit(~(0.U)((p(DATA_WIDTH) * 2).W))
    val divisor = RegInit(~(0.U)((p(DATA_WIDTH) + 1).W))

    val divResult = RegInit(0.U(p(DATA_WIDTH).W))
    val remResult = WireInit(0.U(p(DATA_WIDTH).W))

    val subResult = WireInit(0.U((p(DATA_WIDTH) + 1).W))
    val isSubMinus = WireInit(false.B)

    val abs1 = WireInit(0.U(p(DATA_WIDTH).W))
    val abs2 = WireInit(0.U(p(DATA_WIDTH).W))

    abs1 := Mux(
        isSigned & io.div_input_info.data1(p(DATA_WIDTH) - 1),
        ~(io.div_input_info.data1(p(DATA_WIDTH) - 1, 0)) + 1.U,
        io.div_input_info.data1
    )

    abs2 := Mux(
        isSigned & io.div_input_info.data2(p(DATA_WIDTH) - 1),
        ~(io.div_input_info.data2(p(DATA_WIDTH) - 1, 0)) + 1.U,
        io.div_input_info.data2
    )

    val zero_ext_abs1 = WireInit(0.U((p(DATA_WIDTH) * 2).W))
    zero_ext_abs1 := Cat(
        Fill(32, false.B), abs1
    )

    val zero_ext_abs2 = WireInit(0.U((p(DATA_WIDTH) + 1).W))
    zero_ext_abs2 := Cat(false.B, abs2)

    divResultIsMinus := isSigned & (
        io.div_input_info.data1(p(DATA_WIDTH) - 1) ^ io.div_input_info.data2(p(DATA_WIDTH) - 1)
    )

    remResultIsMinus := isSigned & io.div_input_info.data1(p(DATA_WIDTH) - 1)

    io.divReady := cycleCounter === 33.U

    cycleCounter := Mux(
        io.divReady && io.div_input_info.valid,
        0.U,
        Mux(
            io.div_input_info.valid, 
            cycleCounter + 1.U,
            cycleCounter
        )
    )

    isSubMinus := divEnd(p(DATA_WIDTH) * 2 - 1, p(DATA_WIDTH) - 1) < divisor
    when(!isSubMinus && !io.divReady) {
        divResult := divResult + (1.U << (p(DATA_WIDTH).U - cycleCounter))
    }.otherwise {
        divResult := Mux(io.divReady, 0.U, divResult)
    }

    // do sub
    subResult := Mux(
        isSubMinus, 
        divEnd(p(DATA_WIDTH) * 2 - 1, p(DATA_WIDTH) - 1), 
        divEnd(p(DATA_WIDTH) * 2 - 1, p(DATA_WIDTH) - 1) - divisor
    )

    remResult := divEnd(p(DATA_WIDTH) * 2 - 1, p(DATA_WIDTH))

    divEnd := Mux(
        cycleCounter === 0.U,
        zero_ext_abs1,
        (Cat(subResult, divEnd(p(DATA_WIDTH)-2,0)) << 1.U)(2*p(DATA_WIDTH)-1,0)
    )

    divisor := Mux(cycleCounter === 0.U, zero_ext_abs2, divisor)

    // ffff fffb
    io.divResult := Mux(
        io.divReady, 
        Mux(
            divisor =/= 0.U, 
            Mux(divResultIsMinus, ~divResult + 1.U, divResult),
            ~(0.U)(p(DATA_WIDTH).W)
        ),
        ~(0.U)(p(DATA_WIDTH).W)
    )

    io.remResult := Mux(
        io.divReady, 
        Mux(
            divisor =/= 0.U, 
            Mux(remResultIsMinus, ~remResult + 1.U, remResult),
            ~(0.U)(p(DATA_WIDTH).W)
        ), 
        ~(0.U)(p(DATA_WIDTH).W)
    )

    io.resValid := io.divReady && io.div_input_info.valid
}