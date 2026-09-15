package MyCPU.utils

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._

case class internal_sram_impl(sram_name: String, idx_width: Int, data_width: Int) extends ExtModule (
  Map("name" -> sram_name, "idx_width" -> idx_width, "data_width" -> data_width)
) {

  val io = IO(new Bundle {
    val clock = Input(Clock())
    val reset = Input(Reset())
    val en = Input(Bool())
    val wen = Input(Bool())
    val addr = Input(UInt(idx_width.W))
    val mask = Input(UInt(data_width.W))
    val wdata = Input(UInt(data_width.W))
    val rdata = Output(UInt(data_width.W))
  })

  addPath("/home/ljk/Arch/ChiselLab/MyCPU/src/main/resources/vsrc/utils/internal_sram_impl.sv")
}

class SRAMImpl[T <: Data](depth: Int, gen: T, name: String = "") extends Module {
  val idx_width = log2Ceil(depth)
  val data_width = gen.getWidth

  val io = IO(new Bundle {
    val en = Input(Bool())
    val wen = Input(Bool())
    val addr = Input(UInt(idx_width.W))
    val mask = Input(UInt(data_width.W))
    val wdata = Input(UInt(data_width.W))
    val rdata = Output(UInt(data_width.W))
  })

  val impl = Module(new internal_sram_impl(name, idx_width, data_width))

  impl.io.clock := clock
  impl.io.reset := reset
  impl.io.addr := io.addr
  impl.io.mask := io.mask
  impl.io.wdata := io.wdata
  impl.io.en := io.en
  impl.io.wen := io.wen
  io.rdata := impl.io.rdata
}