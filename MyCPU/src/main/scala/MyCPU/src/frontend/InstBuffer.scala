package MyCPU.frontend

import chisel3._ 
import chisel3.util._ 
import MyCPU.config._ 
import MyCPU.io._ 
import MyCPU.utils._
import org.chipsalliance.cde.config._

// if1 enq, deq before decode, used as a pipelinestage
// has a valid inst, then enqValid is true
class InstBuffer(size: Int)(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val flush = Input(Bool())
        val stall = Input(Bool())

        val inst_i = Input(Vec(p(FETCH_WIDTH), UInt(p(DATA_WIDTH).W)))
        val instValid_i = Input(Vec(p(FETCH_WIDTH), Bool()))

        val inst_o = Output(Vec(p(FETCH_WIDTH), UInt(p(DATA_WIDTH).W)))
        val instValid_o = Output(Vec(p(FETCH_WIDTH), Bool()))
    })

    val qs = Seq.fill(p(FETCH_WIDTH)) {
        Module(new Queue(UInt(p(DATA_WIDTH).W), size, pipe=true, flow=false))
    }

    for(i <- 0 until p(FETCH_WIDTH)) {

        // enqueue
        qs(i).io.enq.bits  := io.inst_i(i)
        qs(i).io.enq.valid := io.instValid_i.asUInt.orR && !io.stall

        // dequeue
        qs(i).io.deq.ready := !io.stall

        // output
        io.inst_o(i) := qs(i).io.deq.bits
        io.instValid_o(i) := qs(i).io.deq.valid
    }

    // flush logic
    when(io.flush) {
        for(i <- 0 until p(FETCH_WIDTH)) {
            qs(i).reset := true.B
        }
    }
}
