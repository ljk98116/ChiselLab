package MyCPU.io

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._

class ITLBReqInfo extends Bundle {
    val vpn = UInt(20.W) // virtual page number
    val asid = UInt(16.W) // address space identifier
}