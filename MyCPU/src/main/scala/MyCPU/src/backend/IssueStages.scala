package MyCPU.backend

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

// enq -> select(use last newest issue queue state)
class IssueStages(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val mem_uops = Output(Vec(p(FETCH_WIDTH), new uOp))
        val alu_uops = Output(Vec(p(FETCH_WIDTH), new uOp))
        // val sys_uops = Output(Vec(p(FETCH_WIDTH), new uOp))
        
    })

    
}