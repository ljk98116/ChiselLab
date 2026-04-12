package MyCPU.middleend

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._

import org.chipsalliance.cde.config._
import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

// MultiPort Write Single Read Queue
class CheckPointQueue(size: Int)(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val flush = Input(Bool())
        val enq = Input(Vec(p(FETCH_WIDTH), new CheckPointInfo))
        val deq = Output(new CheckPointInfo)
    })

    val queue = Module(new MultiPortQueueNW1R(size, p(FETCH_WIDTH), new CheckPointInfo))

    queue.io.flush := io.flush
    queue.io.enq := io.enq
    queue.io.enqValid := VecInit(io.enq.map(_.valid))
    queue.io.deqValid := io.flush

    io.deq.freeRegIdBufferHeads := queue.io.deq.freeRegIdBufferHeads
    io.deq.valid := queue.io.deqReady && io.flush
}