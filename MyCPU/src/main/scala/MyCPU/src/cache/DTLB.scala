package MyCPU.cache

import chisel3._
import chisel3.util._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

import org.chipsalliance.cde.config._

// multi port access, may have multiple refill reqs and multiple refill resps
class DTLB(size: Int)(implicit p: Parameters) extends Module {
    // sigle way sram ports
    val readPortNum = p(LDU_NUM)
    val writePortNum = p(STU_NUM)
    val bankNum = writePortNum + readPortNum

    val io = IO(new Bundle {
        val reqs = Input(Vec(bankNum, new DTLBReqInfo))
    })    
}

