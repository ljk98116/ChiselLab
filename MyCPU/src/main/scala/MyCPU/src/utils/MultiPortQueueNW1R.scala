package MyCPU.utils

import chisel3._
import chisel3.util._
import MyCPU.io._

// Mem Impl[LutRam]
class MultiPortQueueNW1R[T <: Bundle with HasValid](size: Int, nWritePorts: Int, gen: T) extends Module {
    val io = IO(new Bundle {
        val flush = Input(Bool())
        val enq = Input(Vec(nWritePorts, gen))
        val enqValid = Input(Vec(nWritePorts, Bool()))
        val enqReady = Output(Bool())
        val deq = Output(gen)
        val deqValid = Input(Bool())
        val deqReady = Output(Bool())
    })
    
    val ptrWidth = log2Ceil(size)
    val bankBits = log2Ceil(nWritePorts)
    val perBankSize = size / nWritePorts

    val headPtr = RegInit(0.U(ptrWidth.W))
    val tailPtr = RegInit(0.U(ptrWidth.W))

    val writeCount = PopCount(io.enqValid)

    val headPtrBank = headPtr(bankBits - 1, 0)
    val headPtrRow = headPtr(ptrWidth - 1, bankBits)

    val used = tailPtr - headPtr
    val free = size.U - used

    val banks = Seq.fill(nWritePorts) {
        if (perBankSize < 8)
            Module(new SmallBank(perBankSize, gen))
        else
            Module(new LargeBank(perBankSize, gen))
    }

    io.enqReady := used + writeCount <= size.U
    io.deqReady := headPtr =/= tailPtr

    for(i <- 0 until nWritePorts) {
        val wr_index = tailPtr + i.U
        val wr_bank = wr_index(bankBits - 1, 0)
        val wr_row = wr_index(ptrWidth - 1, bankBits)

        for(b <- 0 until nWritePorts) {
            banks(b).io.wen := false.B
            banks(b).io.waddr := 0.U
            banks(b).io.wdata := 0.U.asTypeOf(gen)
            banks(b).io.wmask := (~(0.U)).asTypeOf(gen)
            when(b.U === wr_bank && io.enqReady && io.enqValid(i)) {
                banks(b).io.wen := true.B
                banks(b).io.waddr := wr_row
                banks(b).io.wdata := io.enq(i)               
            }
        }
    }

    val readVec = Wire(Vec(nWritePorts, gen))

    for(i <- 0 until nWritePorts) {
        banks(i).io.raddr := headPtrRow
        readVec(i) := banks(i).io.rdata
    }

    io.deq := Mux(io.deqValid, readVec(headPtrBank), 0.U.asTypeOf(gen))

    headPtr := Mux(io.flush, 0.U, headPtr + (io.deqReady && io.deqValid))
    tailPtr := Mux(io.flush, 0.U, tailPtr + writeCount)
}