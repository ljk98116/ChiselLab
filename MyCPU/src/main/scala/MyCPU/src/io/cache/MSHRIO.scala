package MyCPU.io

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._

class ICacheMSHREntry(implicit p: Parameters) extends Bundle with HasValid {
  val paddr = new PhysicalAddress
  val valid = Bool()
  val mshr_id = UInt(log2Ceil(p(ICacheMSHRNum)).W)
}

class DCacheMSHREntry(implicit p: Parameters) extends Bundle with HasValid {
  val paddr = new PhysicalAddress
  val valid = Bool()
  val mshr_id = UInt(log2Ceil(p(DCacheMSHRNum)).W)
}
