package MyCPU.utils

import chisel3._
import chisel3.util._

class Mux1HTest(width: Int) extends Module {
    val io = IO(new Bundle {
        val in = Input(UInt(width.W));
        val out = Output(UInt(log2Ceil(width).W))
        val valid = Output(Bool())
    })

    io.valid := io.in.orR;
    io.out := Mux(io.valid, OHToUInt(io.in.asBools), 0.U);
}