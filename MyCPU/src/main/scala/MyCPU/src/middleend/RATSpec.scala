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
        val commit_refill_write_back = Flipped(DecoupledIO(Vec(p(FETCH_WIDTH), new RATSpecWriteReq)))
    })

    val spec::refill::Nil = Enum(2)
    val state = RegInit(spec)
    val nextState = WireDefault(state)
    val refillNum = (1 << p(AREG_WIDTH)) / p(FETCH_WIDTH)
    val refillCounterWidth = log2Ceil(refillNum)

    val refillCounter = RegInit(0.U(refillCounterWidth.W))

    val RATSpecTable = RegInit(VecInit(
        Seq.fill(1 << p(AREG_WIDTH))(0.U.asTypeOf(new RATSpecEntry))
    ))

    nextState := Mux(io.flush || state === refill && refillCounter === (refillNum - 1).U, ~state, state)
    state := nextState

    when(state === refill) {
        when(io.commit_refill_write_back.valid) {
            refillCounter := refillCounter + 1.U
        }
    }.otherwise {
        refillCounter := 0.U
    }

    when(state === spec) {
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
        io.commit_refill_write_back.ready := false.B
    }.otherwise {
        for(i <- 0 until p(FETCH_WIDTH)) {
            io.rs1_rat_read_resp(i) := 0.U.asTypeOf(new RATSpecReadResp)
            io.rs2_rat_read_resp(i) := 0.U.asTypeOf(new RATSpecReadResp)
            io.rd_rat_read_resp(i) := 0.U.asTypeOf(new RATSpecReadResp)
        }
        when(io.commit_refill_write_back.valid) {
            for(i <- 0 until p(FETCH_WIDTH)) {
                RATSpecTable(io.commit_refill_write_back.bits(i).areg).preg := io.commit_refill_write_back.bits(i).preg
                RATSpecTable(io.commit_refill_write_back.bits(i).areg).valid := io.commit_refill_write_back.bits(i).valid
            }
            io.commit_refill_write_back.ready := true.B
        }.otherwise {
            io.commit_refill_write_back.ready := false.B
        }
    }
}