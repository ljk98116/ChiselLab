package MyCPU.backend

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

class SYSEXU(implicit p : Parameters) extends Module {
    val io = IO(new Bundle {
        val flush = Input(Bool())
        val stall_from_backpipe = Input(Bool())
        val sys_item = Input(new SYSEXUInputInfo)

        val result = Output(new SysResult)
        val uop = Output(new SYSIQEntry)    
    })    

    val result = WireInit(0.U.asTypeOf(new SysResult))
    // to do, add impls
    
    io.result := result
    io.uop := io.sys_item.uop
}

