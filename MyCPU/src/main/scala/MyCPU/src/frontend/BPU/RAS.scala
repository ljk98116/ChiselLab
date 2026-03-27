package MyCPU.frontend.BPU

import chisel3._
import chisel3.util._

import MyCPU.config._
import MyCPU.io._
import org.chipsalliance.cde.config._
import MyCPU.utils._

// if2 modify RAS, use first call/return inst 
class RAS(implicit p: Parameters) extends Module {
    val ptrWidth = log2Ceil(p(RASParam).RASSize)

    val io = IO(new Bundle {
        // spec update
        val if2_isCall = Input(Vec(p(FETCH_WIDTH), Bool()))
        val if2_isReturn = Input(Vec(p(FETCH_WIDTH), Bool()))
        val if2_pcVec = Input(Vec(p(FETCH_WIDTH), UInt(p(ADDR_WIDTH).W)))
        val if2_pcValid = Input(Vec(p(FETCH_WIDTH), Bool()))
        // spec result
        val rasTopPtr = Output(UInt(ptrWidth.W))
        val rasTop = Output(UInt(p(ADDR_WIDTH).W))
        // checkpoint recover, if3/commit
        val redirectValid = Input(Bool())
        val redirectIsCall = Input(Bool())
        val redirectIsReturn = Input(Bool())
        val redirectRasTopPtr = Input(UInt(ptrWidth.W))
        val redirectPC = Input(UInt(p(ADDR_WIDTH).W))
    })

    val rasMem = Mem(p(RASParam).RASSize, UInt(p(ADDR_WIDTH).W)) // lut ram
    val rasTopPtr = RegInit(0.U)

    def rasTopInc(sp: UInt) = {
        Mux(sp + 1.U > (p(RASParam).RASSize - 1).U, sp, sp + 1.U)
    }

    def rasTopDec(sp: UInt) = {
        Mux(sp - 1.U >= 0.U, sp - 1.U, sp)
    }    

    val if2_isCallOrReturn = WireInit(VecInit(
        Seq.fill(p(FETCH_WIDTH))(false.B)
    ))

    for(i <- 0 until p(FETCH_WIDTH)) {
        if2_isCallOrReturn(i) := (io.if2_isCall(i) || io.if2_isReturn(i)) && io.if2_pcValid(i)
    }

    val if2_firstCallOrReturnIndex = PriorityEncoder(if2_isCallOrReturn)
    val if2_hasCallOrReturn = if2_isCallOrReturn.asUInt.orR

    val nextRasTopPtr = WireInit(0.U(ptrWidth.W)) 
    val nextRasTop = WireInit(0.U(p(ADDR_WIDTH).W))
    val needUpdate = WireInit(false.B)

    when(io.redirectValid) {
        when(io.redirectIsCall) {
            nextRasTopPtr := rasTopInc(io.redirectRasTopPtr)
            nextRasTop := io.redirectPC + 4.U
            needUpdate := true.B
        }.elsewhen(io.redirectIsReturn) {
            nextRasTopPtr := io.redirectRasTopPtr - 1.U
            nextRasTop := 0.U
            needUpdate := false.B
        }
    }.otherwise {
        when(if2_hasCallOrReturn) {
            nextRasTopPtr := Mux(
                io.if2_isCall(if2_firstCallOrReturnIndex), rasTopInc(rasTopPtr), 
                Mux(io.if2_isReturn(if2_firstCallOrReturnIndex), rasTopDec(rasTopPtr), rasTopPtr)
            )
            nextRasTop := Mux(
                io.if2_isCall(if2_firstCallOrReturnIndex), io.if2_pcVec(if2_firstCallOrReturnIndex) + 4.U,
                0.U
            )
        }
        needUpdate := if2_hasCallOrReturn && io.if2_isCall(if2_firstCallOrReturnIndex)
    }

    when(needUpdate) {
        rasMem.write(nextRasTopPtr, nextRasTop)
    }

    rasTopPtr := nextRasTopPtr
    io.rasTopPtr := rasTopPtr
    io.rasTop := rasMem.read(rasTopPtr)
}