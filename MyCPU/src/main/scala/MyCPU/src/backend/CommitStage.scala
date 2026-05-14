package MyCPU.backend

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

// rob read at this stage, has read latency
// rob read + signal judge
class CommitStage(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val flush = Output(Bool())
        val commit_uops = Input(Vec(p(FETCH_WIDTH), new uOp))
        val commit_valid = Input(Bool())

    })

    val commit_valid = PipeReg(io.commit_valid, false.B, io.flush)

}

