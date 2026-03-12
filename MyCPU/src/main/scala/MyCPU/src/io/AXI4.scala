package MyCPU.io

import chisel3._
import chisel3.util._

import MyCPU.config._

class AXI4Aw(addrWidth: Int, idWidth: Int) extends Bundle {
  val id    = UInt(idWidth.W)
  val addr  = UInt(addrWidth.W)
  val len   = UInt(8.W)
  val size  = UInt(3.W)
  val burst = UInt(2.W)
}

class AXI4W(dataWidth: Int) extends Bundle {
  val data = UInt(dataWidth.W)
  val strb = UInt((dataWidth/8).W)
  val last = Bool()
}

class AXI4B(idWidth: Int) extends Bundle {
  val id   = UInt(idWidth.W)
  val resp = UInt(2.W)
}

class AXI4Ar(addrWidth: Int, idWidth: Int) extends Bundle {
  val id    = UInt(idWidth.W)
  val addr  = UInt(addrWidth.W)
  val len   = UInt(8.W)
  val size  = UInt(3.W)
  val burst = UInt(2.W)
}

class AXI4R(dataWidth: Int, idWidth: Int) extends Bundle {
  val id   = UInt(idWidth.W)
  val data = UInt(dataWidth.W)
  val resp = UInt(2.W)
  val last = Bool()
}

class AXI4(addrWidth: Int, dataWidth: Int, idWidth: Int) extends Bundle {

  val aw = Decoupled(new AXI4Aw(addrWidth, idWidth))
  val w  = Decoupled(new AXI4W(dataWidth))
  val b  = Flipped(Decoupled(new AXI4B(idWidth)))

  val ar = Decoupled(new AXI4Ar(addrWidth, idWidth))
  val r  = Flipped(Decoupled(new AXI4R(dataWidth, idWidth)))
}
