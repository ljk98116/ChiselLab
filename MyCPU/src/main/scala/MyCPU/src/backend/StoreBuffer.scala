package MyCPU.backend

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

// load check pipeline
// stage0 read all datas and check equal[load stage0]
// stage1 send check result [load stage1]
// stage0 store enq
// when dcache write ready, deq, drop head
class StoreBuffer(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {

    })

    
}