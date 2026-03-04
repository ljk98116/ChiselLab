package MyCPU.utils

import chisel3._
import chisel3.util._
import MyCPU.io._

abstract class BaseBank[T <: Data](depth: Int, gen: T) extends Module {
    val io = IO(new Bundle {
        val wen   = Input(Bool())
        val waddr = Input(UInt(log2Ceil(depth).W))
        val wdata = Input(gen)

        val raddr = Input(UInt(log2Ceil(depth).W))
        val rdata = Output(gen)
    })
}

class SmallBank[T <: Data](depth: Int, gen: T) extends BaseBank(depth, gen) {

    val mem = RegInit(VecInit(Seq.fill(depth)(0.U.asTypeOf(gen))))

    when(io.wen) {
        mem(io.waddr) := io.wdata
    }

    io.rdata := mem(io.raddr)
}

class LargeBank[T <: Data](depth: Int, gen: T) extends BaseBank(depth, gen) {

    val mem = Mem(depth, gen)

    when(io.wen) {
        mem.write(io.waddr, io.wdata)
    }

    io.rdata := mem.read(io.raddr)
}

