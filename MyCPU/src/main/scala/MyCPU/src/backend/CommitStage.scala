package MyCPU.backend

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

// rob read at this stage, has read latency
// rob read + signal judge
// also used as wb1
class CommitStage(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val flush = Output(Bool())
        val commit_uops = Input(Vec(p(FETCH_WIDTH), new uOp))
        val commit_valid = Input(Bool())
        val storeEnable = Output(Vec(p(FETCH_WIDTH), new StoreQueueEnable))
    })

    val commit_valid = PipeReg(io.commit_valid, false.B, io.flush)

    for(b <- 0 until p(FETCH_WIDTH)) {
        io.storeEnable(b).valid := io.commit_uops(b).valid && (io.commit_uops(b).opcode === OpcodeVal.SB)
        io.storeEnable(b).robLoc := io.commit_uops(b).rob_loc
    }

    
}

