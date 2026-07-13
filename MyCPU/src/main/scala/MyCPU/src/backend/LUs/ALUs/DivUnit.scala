package MyCPU.backend

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

// get abs value for signed div, use abs value to div
// use abs value to select final result
// non pipeline div
class DivUnit(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val flush = Input(Bool())
        val stall_from_backpipe = Input(Bool())
        val div_input_info = Input(new DivUnitInputInfo)
        val divResult = Output(UInt(p(DATA_WIDTH).W))
        val remResult = Output(UInt(p(DATA_WIDTH).W))
        val divReady = Output(Bool())      
    })

    val isREM = io.div_input_info.funct3(1)
    val isDIV = !io.div_input_info.funct3(1)
    val isSigned = !io.div_input_info.funct3(0)

    val divResultIsMinus = RegInit(false.B)
    val remResultIsMinus = RegInit(false.B)

    // at most 34 periods
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

    val zero_ext_abs1 = RegInit(0.U((p(DATA_WIDTH) * 2).W))
    zero_ext_abs1 := Cat(
        Fill(32, false.B), abs1
    )

    val zero_ext_abs2 = RegInit(0.U((p(DATA_WIDTH) + 1).W))
    zero_ext_abs2 := Cat(false.B, abs2)

    divResultIsMinus := isSigned & (
        io.div_input_info.data1(p(DATA_WIDTH) - 1) ^ io.div_input_info.data2(p(DATA_WIDTH) - 1)
    )

    remResultIsMinus := isSigned & io.div_input_info.data1(p(DATA_WIDTH) - 1)

    io.divReady := cycleCounter === 35.U

    cycleCounter := Mux(
        io.divReady,
        0.U,
        cycleCounter + io.div_input_info.valid.asUInt
    )

    isSubMinus := divEnd(p(DATA_WIDTH) * 2 - 1, p(DATA_WIDTH) - 1) < divisor

    when(!isSubMinus && !io.divReady) {
        divResult := divResult + (1.U << ((p(DATA_WIDTH) + 1).U - cycleCounter))
    }.otherwise {
        divResult := Mux(cycleCounter === 35.U, 0.U, divResult)
    }

    // do sub
    subResult := Mux(
        isSubMinus, 
        divEnd(p(DATA_WIDTH) * 2 - 1, p(DATA_WIDTH) - 1), 
        divEnd(p(DATA_WIDTH) * 2 - 1, p(DATA_WIDTH) - 1) - divisor
    )

    remResult := divEnd(p(DATA_WIDTH) * 2 - 1, p(DATA_WIDTH))

    divEnd := Mux(
        cycleCounter === 1.U,
        zero_ext_abs1,
        (Cat(subResult, divEnd(p(DATA_WIDTH)-2,0)) << 1.U)(2*p(DATA_WIDTH)-1,0)
    )

    divisor := Mux(cycleCounter === 1.U, zero_ext_abs2, divisor)

    // ffff fffb
    io.divResult := Mux(RegNext(divResultIsMinus), ~RegNext(divResult) + 1.U, RegNext(divResult))
    io.remResult := Mux(RegNext(remResultIsMinus), ~RegNext(remResult) + 1.U, RegNext(remResult))
}

// to do: pipelined div
class DivUnitPipeLine(pipeLen: Int)(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val flush = Input(Bool())
        val stall_from_backpipe = Input(Bool())
        val div_input_info = Input(new DivUnitInputInfo)
        val divResult = Output(UInt(p(DATA_WIDTH).W))
        val remResult = Output(UInt(p(DATA_WIDTH).W))
        val divReady = Output(Bool())      
    })

    require(pipeLen == 33 || pipeLen == 34)

    // 1st stage prepare op datas
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

    val zero_ext_abs1 = RegInit(0.U((p(DATA_WIDTH) * 2).W))
    zero_ext_abs1 := Cat(
        Fill(32, false.B), abs1
    )

    val zero_ext_abs2 = RegInit(0.U((p(DATA_WIDTH) + 1).W))
    zero_ext_abs2 := Cat(false.B, abs2)

    val divisor_pipeLine = Seq.fill()
}