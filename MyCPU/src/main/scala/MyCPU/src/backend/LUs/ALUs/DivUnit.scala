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

    val isREM = RegNext(io.div_input_info.funct3(1))
    val isDIV = RegNext(!io.div_input_info.funct3(1))
    val isSigned = RegNext(!io.div_input_info.funct3(0))

    val divResultIsMinus = RegInit(false.B)
    val remResultIsMinus = RegInit(false.B)

    // at most 34 periods
    val cycleCounter = RegInit(0.U((log2Ceil(p(DATA_WIDTH) + 1)).W))

    // all bits preset to 1
    val divEnd = RegInit(~(0.U)((p(DATA_WIDTH) * 2).W))
    val divisor = RegInit(~(0.U)((p(DATA_WIDTH) + 1).W))

    val divResult = RegInit(0.U(p(DATA_WIDTH).W))
    val remResult = WireInit(0.U(p(DATA_WIDTH).W))

    // Final signed correction used to be on the output:
    //   Mux(sign, ~RegNext(result) + 1.U, RegNext(result))
    // That leaves a full DATA_WIDTH incrementer on the divider result path.  Split the
    // two's-complement correction into low/high halves and register the final outputs.
    // The divider gets one extra correction cycle, but the critical incrementer width is
    // reduced from DATA_WIDTH to roughly DATA_WIDTH/2 and the IO path is flop-to-output.
    require(p(DATA_WIDTH) >= 2 && p(DATA_WIDTH) % 2 == 0)
    private val loWidth = p(DATA_WIDTH) / 2
    private val hiWidth = p(DATA_WIDTH) - loWidth

    val divResultOut = RegInit(0.U(p(DATA_WIDTH).W))
    val remResultOut = RegInit(0.U(p(DATA_WIDTH).W))
    val divRawHiReg = RegInit(0.U(hiWidth.W))
    val remRawHiReg = RegInit(0.U(hiWidth.W))
    val divLoCorrectedReg = RegInit(0.U(loWidth.W))
    val remLoCorrectedReg = RegInit(0.U(loWidth.W))
    val divHiCarryReg = RegInit(false.B)
    val remHiCarryReg = RegInit(false.B)
    val divCorrectSignReg = RegInit(false.B)
    val remCorrectSignReg = RegInit(false.B)

    val subResult = WireInit(0.U((p(DATA_WIDTH) + 1).W))
    val isSubMinus = WireInit(false.B)

    val op1 = RegInit(0.U(p(DATA_WIDTH).W))
    val op2 = RegInit(0.U(p(DATA_WIDTH).W))

    op1 := io.div_input_info.data1
    op2 := io.div_input_info.data2

    // Split input absolute-value generation too.  This removes the other full-width
    // (~x + 1) from the divider: low half is corrected from the captured operands,
    // high half is corrected in the following cycle with only a half-width increment.
    val abs1RawHiReg = RegInit(0.U(hiWidth.W))
    val abs2RawHiReg = RegInit(0.U(hiWidth.W))
    val abs1LoReg = RegInit(0.U(loWidth.W))
    val abs2LoReg = RegInit(0.U(loWidth.W))
    val abs1HiCarryReg = RegInit(false.B)
    val abs2HiCarryReg = RegInit(false.B)
    val abs1NegReg = RegInit(false.B)
    val abs2NegReg = RegInit(false.B)

    val op1Neg = isSigned & op1(p(DATA_WIDTH) - 1)
    val op2Neg = isSigned & op2(p(DATA_WIDTH) - 1)
    val op1Lo = op1(loWidth - 1, 0)
    val op2Lo = op2(loWidth - 1, 0)
    val op1Hi = op1(p(DATA_WIDTH) - 1, loWidth)
    val op2Hi = op2(p(DATA_WIDTH) - 1, loWidth)
    val abs1NegLo = (~op1Lo).asUInt + 1.U
    val abs2NegLo = (~op2Lo).asUInt + 1.U

    abs1RawHiReg := op1Hi
    abs2RawHiReg := op2Hi
    abs1LoReg := Mux(op1Neg, abs1NegLo(loWidth - 1, 0), op1Lo)
    abs2LoReg := Mux(op2Neg, abs2NegLo(loWidth - 1, 0), op2Lo)
    abs1HiCarryReg := op1Neg && (op1Lo === 0.U)
    abs2HiCarryReg := op2Neg && (op2Lo === 0.U)
    abs1NegReg := op1Neg
    abs2NegReg := op2Neg

    val abs1NegHi = (~abs1RawHiReg).asUInt + abs1HiCarryReg.asUInt
    val abs2NegHi = (~abs2RawHiReg).asUInt + abs2HiCarryReg.asUInt
    val abs1 = Cat(Mux(abs1NegReg, abs1NegHi(hiWidth - 1, 0), abs1RawHiReg), abs1LoReg)
    val abs2 = Cat(Mux(abs2NegReg, abs2NegHi(hiWidth - 1, 0), abs2RawHiReg), abs2LoReg)

    val zero_ext_abs1 = Cat(Fill(p(DATA_WIDTH), false.B), abs1)
    val zero_ext_abs2 = Cat(false.B, abs2)

    // RISC-V DIV-by-zero quotient is all ones for both signed and unsigned DIV,
    // so do not sign-correct the quotient when divisor is zero.
    divResultIsMinus := isSigned & (op2 =/= 0.U) & (
        op1(p(DATA_WIDTH) - 1) ^ op2(p(DATA_WIDTH) - 1)
    )

    remResultIsMinus := isSigned & op1(p(DATA_WIDTH) - 1)

    val divIterDone = cycleCounter === 35.U
    val correctHiCycle = cycleCounter === 36.U

    io.divReady := cycleCounter === 37.U

    cycleCounter := Mux(
        io.divReady,
        0.U,
        cycleCounter + io.div_input_info.valid.asUInt
    )

    isSubMinus := divEnd(p(DATA_WIDTH) * 2 - 1, p(DATA_WIDTH) - 1) < divisor

    // no need to use +, because cycleCounter rising
    when(!isSubMinus && !io.divReady) {
        divResult := divResult | (1.U << ((p(DATA_WIDTH) + 2).U - cycleCounter))
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
        cycleCounter === 2.U,
        zero_ext_abs1,
        Cat(subResult(p(DATA_WIDTH)-1, 0), divEnd(p(DATA_WIDTH)-2, 0), false.B)
    )

    divisor := Mux(cycleCounter === 2.U, zero_ext_abs2, divisor)

    // Split final two's-complement correction.  At divIterDone the raw quotient and
    // remainder are the same values the old code exposed through RegNext(...) on the
    // following ready cycle.  Correct the low half first and save the carry into the
    // high-half increment; correct the high half in the next cycle.
    val divRawLo = divResult(loWidth - 1, 0)
    val divRawHi = divResult(p(DATA_WIDTH) - 1, loWidth)
    val remRawLo = remResult(loWidth - 1, 0)
    val remRawHi = remResult(p(DATA_WIDTH) - 1, loWidth)

    val divNegLo = (~divRawLo).asUInt + 1.U
    val remNegLo = (~remRawLo).asUInt + 1.U

    when(divIterDone) {
        divRawHiReg := divRawHi
        remRawHiReg := remRawHi
        divCorrectSignReg := divResultIsMinus
        remCorrectSignReg := remResultIsMinus
        divLoCorrectedReg := Mux(divResultIsMinus, divNegLo(loWidth - 1, 0), divRawLo)
        remLoCorrectedReg := Mux(remResultIsMinus, remNegLo(loWidth - 1, 0), remRawLo)
        // Carry from (~lo + 1) into high half happens iff the original low half is 0.
        divHiCarryReg := divResultIsMinus && (divRawLo === 0.U)
        remHiCarryReg := remResultIsMinus && (remRawLo === 0.U)
    }

    val divNegHi = (~divRawHiReg).asUInt + divHiCarryReg.asUInt
    val remNegHi = (~remRawHiReg).asUInt + remHiCarryReg.asUInt

    when(correctHiCycle) {
        divResultOut := Cat(
            Mux(divCorrectSignReg, divNegHi(hiWidth - 1, 0), divRawHiReg),
            divLoCorrectedReg
        )
        remResultOut := Cat(
            Mux(remCorrectSignReg, remNegHi(hiWidth - 1, 0), remRawHiReg),
            remLoCorrectedReg
        )
    }

    io.divResult := divResultOut
    io.remResult := remResultOut
}

// to do: pipelined div
class DivUnitPipeLine(pipeLen: Int)(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val flush = Input(Bool())
        val stall_from_backpipe = Input(Bool())
        val div_input_info = Input(new DivUnitInputInfo)
        val divResult = Output(UInt(p(DATA_WIDTH).W))
        val remResult = Output(UInt(p(DATA_WIDTH).W))
        val divResValid = Output(Bool())      
    })

    require(pipeLen == 33 || pipeLen == 34)

    // 1st stage prepare op datas
    val abs1 = WireInit(0.U(p(DATA_WIDTH).W))
    val abs2 = WireInit(0.U(p(DATA_WIDTH).W))

    val isREM = io.div_input_info.funct3(1)
    val isDIV = !io.div_input_info.funct3(1)
    val isSigned = !io.div_input_info.funct3(0)

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
        Fill(p(DATA_WIDTH), false.B), abs1
    )

    val zero_ext_abs2 = RegInit(0.U((p(DATA_WIDTH) + 1).W))
    zero_ext_abs2 := Cat(false.B, abs2)

    val divisor_pipeLine = Seq.fill(pipeLen)(
        RegInit(~(0.U)((p(DATA_WIDTH) + 1).W))
    )

    val dividend_pipeLine = Seq.fill(pipeLen)(
        RegInit(~(0.U)((p(DATA_WIDTH) * 2).W))
    )

    
}