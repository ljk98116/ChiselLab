package MyCPU.backend

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._

import org.chipsalliance.cde.config._
import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

// used for Load RAW harvard mark, enq at ex stage
// only store LDU result
// when load reached ROB top, rob enabled, deq
class AGUResultBuffer(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val lduAguResult = Input(Vec(p(LDU_NUM), new AGUResult))
        val ex_stall = Input(Bool())
        val flush = Input(Bool())
        
        
    })

}