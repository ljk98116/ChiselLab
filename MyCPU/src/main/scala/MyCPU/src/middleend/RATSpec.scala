package MyCPU.middleend

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config._
import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

class RATSpec(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        // from RenameStages
        val rs1_rat_read_req = Input(Vec(p(FETCH_WIDTH), new RATSpecReadReq))
        val rs2_rat_read_req = Input(Vec(p(FETCH_WIDTH), new RATSpecReadReq))
        val rd_rat_read_req = Input(Vec(p(FETCH_WIDTH), new RATSpecReadReq))
        val rd_rat_write_req = Input(Vec(p(FETCH_WIDTH), new RATSpecWriteReq))
        // to RenameStages
        val rs1_rat_read_resp = Output(Vec(p(FETCH_WIDTH), new RATSpecReadResp))
        val rs2_rat_read_resp = Output(Vec(p(FETCH_WIDTH), new RATSpecReadResp))
        val rd_rat_read_resp = Output(Vec(p(FETCH_WIDTH), new RATSpecReadResp))
        // from commit stage
        val flush = Input(Bool())
        val commit_refill_write_back = Flipped(DecoupledIO(Vec((1 << p(AREG_WIDTH)), new RATCommitEntry)))
    })

    val RATSpecTable = RegInit(VecInit(
        Seq.fill(1 << p(AREG_WIDTH))(0.U.asTypeOf(new RATSpecEntry))
    ))

    for(i <- 0 until p(FETCH_WIDTH)) {
        // default value, may be overwritten by write req
        io.rs1_rat_read_resp(i).preg := RATSpecTable(io.rs1_rat_read_req(i).areg).preg
        io.rs1_rat_read_resp(i).valid := RATSpecTable(io.rs1_rat_read_req(i).areg).valid && io.rs1_rat_read_req(i).valid

        io.rs2_rat_read_resp(i).preg := RATSpecTable(io.rs2_rat_read_req(i).areg).preg
        io.rs2_rat_read_resp(i).valid := RATSpecTable(io.rs2_rat_read_req(i).areg).valid && io.rs2_rat_read_req(i).valid

        io.rd_rat_read_resp(i).preg := RATSpecTable(io.rd_rat_read_req(i).areg).preg
        io.rd_rat_read_resp(i).valid := RATSpecTable(io.rd_rat_read_req(i).areg).valid && io.rd_rat_read_req(i).valid

        when(io.rd_rat_write_req(i).valid) {
            RATSpecTable(io.rd_rat_write_req(i).areg).preg := io.rd_rat_write_req(i).preg
            RATSpecTable(io.rd_rat_write_req(i).areg).valid := true.B
        }
    }

    when(io.flush && io.commit_refill_write_back.valid) {
        for(i <- 0 until (1 << p(AREG_WIDTH))) {
            RATSpecTable(i).preg := io.commit_refill_write_back.bits(i).preg
            RATSpecTable(i).valid := io.commit_refill_write_back.bits(i).valid
        }
        io.commit_refill_write_back.ready := true.B
    }
}