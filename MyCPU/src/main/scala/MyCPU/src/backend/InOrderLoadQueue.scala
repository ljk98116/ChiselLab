package MyCPU.backend

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

// enq at issue enq stage
// deq when load reach rob top
// used for load RAW harvard check, when store reached rob top, check load raw harvard
// recv load agu Addr from LDU
class InOrderLoadQueue(size: Int)(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {

    })

    
}