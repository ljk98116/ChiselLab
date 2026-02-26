package MyCPU.io

import chisel3._
import chisel3.util._

import MyCPU.config._
import org.chipsalliance.cde.config._

class IF1RedirectInfo(implicit p: Parameters) extends Bundle {
    val redirect = Bool()
    val target = UInt(p(ADDR_WIDTH).W)
    // used for ras recovery, marked as ras top operated after if1 pred 
    val rasTopPtr = UInt(log2Ceil(p(RASParam).RASSize).W)
}