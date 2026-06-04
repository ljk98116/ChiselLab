package MyCPU.cache

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._

class DCacheWaitQueue(size: Int)(implicit p: Parameters) extends Module {
    // sigle way sram ports
    val readPortNum = p(LDU_NUM)
    val writePortNum = p(STU_NUM)
    val bankNum = writePortNum + readPortNum
    // use writePortNum as bankNum
    // copy readPortNum within a bank
    val bankBits = log2Ceil(bankNum)
    val io = IO(new Bundle {
        val flush = Input(Bool())
        val mem3_dcache_wb_req = Input(Vec(bankNum, new DCacheWriteBackReq))
        val mem3_dcache_wb_buffer_enqReady = Output(Bool())
        
    })

}