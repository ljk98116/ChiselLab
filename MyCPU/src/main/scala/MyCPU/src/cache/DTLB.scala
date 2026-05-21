package MyCPU.cache

import chisel3._
import chisel3.util._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

import org.chipsalliance.cde.config._

// multi port access, may have multiple refill reqs
class DTLB(size: Int)(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val ldReqs = Input(Vec(p(LDU_NUM), new DTLBReqInfo))
        val stReqs = Input(Vec(p(STU_NUM), new DTLBReqInfo))
    })    
}

