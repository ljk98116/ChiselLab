package MyCPU.backend

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

class RATCommit(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    val rd_rat_write_req = Input(Vec(p(FETCH_WIDTH), new RATCommitWriteReq))
    val flush = Input(Bool())
    val commit_refill_write_back = DecoupledIO(Vec((1 << p(AREG_WIDTH)), new RATCommitEntry))
  })

  val RATCommitTable = RegInit(VecInit(
    Seq.fill(1 << p(AREG_WIDTH))(0.U.asTypeOf(new RATCommitEntry))
  ))

  for(i <- 0 until p(FETCH_WIDTH)) {
    when(io.rd_rat_write_req(i).valid) {
      RATCommitTable(io.rd_rat_write_req(i).areg).preg := io.rd_rat_write_req(i).preg
      RATCommitTable(io.rd_rat_write_req(i).areg).valid := true.B
    }
  }

  io.commit_refill_write_back.bits := RATCommitTable
  io.commit_refill_write_back.valid := io.flush
}