package MyCPU.utils

import chisel3._
import chisel3.util._
import MyCPU.io._

// Mem Impl[LutRam]
// must write mask as "b011" or "b001", must not write "b101"
class MultiPortQueueNWNR[T <: Bundle with HasValid](size: Int, nPorts: Int, gen: T) extends Module {
    val io = IO(new Bundle {
        val flush = Input(Bool())
        val enq = Input(Vec(nPorts, gen))

        val enqValid = Input(Vec(nPorts, Bool()))
        val enqReady = Output(Bool())

        val deqValid = Input(Vec(nPorts, Bool()))
        val deq = Output(Vec(nPorts, gen))
        val deqReady = Output(Bool())
    })
    
    val ptrWidth = log2Ceil(size)
    val bankBits = log2Ceil(nPorts)
    val perBankSize = size / nPorts

    val headPtr = RegInit(0.U(ptrWidth.W))
    val tailPtr = RegInit(0.U(ptrWidth.W))

    val writeCount = PopCount(io.enqValid)
    val readCount = PopCount(io.deqValid)

    val used = tailPtr - headPtr
    val free = size.U - used

    val banks = Seq.fill(nPorts) {
        new LargeBank(clock, reset, perBankSize, gen)
    }

    io.enqReady := used + writeCount <= size.U
    io.deqReady := free + readCount <= size.U

    for(i <- 0 until nPorts) {
        val wr_index = tailPtr + i.U
        val wr_bank = wr_index(bankBits - 1, 0)
        val wr_row = wr_index(ptrWidth - 1, bankBits)

        for(b <- 0 until nPorts) {
            when(b.U === wr_bank && io.enqReady && io.enqValid(i)) {
                banks(b).write(
                    wr_row, ~(0.U), io.enq(i).asUInt
                )            
            }
        }
    }

    val readVec = Wire(Vec(nPorts, gen))

    for(i <- 0 until nPorts) {
        val rd_index = headPtr + i.U
        val rd_bank = rd_index(bankBits - 1, 0)
        val rd_row = rd_index(ptrWidth - 1, bankBits)

        for(b <- 0 until nPorts) {
            when(b.U === rd_bank && io.deqReady && io.deqValid(i)) {
                readVec(i) := banks(b).read(rd_row)
            }
        }
    }

    io.deq := readVec

    headPtr := Mux(io.flush, 0.U, headPtr + readCount)
    tailPtr := Mux(io.flush, 0.U, tailPtr + writeCount)
}