package MyCPU.frontend.BPU

import chisel3._
import chisel3.util._

import MyCPU.config._
import MyCPU.io._
import org.chipsalliance.cde.config._

// Multi Write/ Single Read queue for L1 BTB update
// multi bank write to regfile, combine read
class L1BTBWbQueue(size: Int)(implicit p: Parameters) extends Module {
    val io = IO(new Bundle{
        val enq = Input(Vec(p(FETCH_WIDTH), new L1BTBUpdateInfo))
        val deq = Output(new L1BTBUpdateInfo)
        val rd_able = Output(Bool())
        val wr_able = Output(Bool())
    })

    val ptrWidth = log2Ceil(size / p(FETCH_WIDTH))

    val banks = RegInit(VecInit(
        Seq.fill(p(FETCH_WIDTH))(
            VecInit(Seq.fill(size / p(FETCH_WIDTH))(0.U.asTypeOf(new L1BTBUpdateInfo)))
        )
    ))

    val head = RegInit(0.U(ptrWidth.W))
    val tail = RegInit(0.U(ptrWidth.W))
    val count = RegInit(0.U((ptrWidth + 1).W))
    val prefix = Wire(Vec(p(FETCH_WIDTH), UInt(log2Ceil(p(FETCH_WIDTH)+1).W)))

    prefix(0) := 0.U
    for(i <- 1 until p(FETCH_WIDTH)){
        prefix(i) := prefix(i-1) + io.enq(i-1).valid
    }
 
    // write logic 
    for(i <- 0 until p(FETCH_WIDTH)) {
        val global_index = tail + prefix(i)
        val bank = global_index & ((1 << log2Ceil(p(FETCH_WIDTH))) - 1).U
        val row = global_index >> log2Ceil(p(FETCH_WIDTH)).U
        for(j <- 0 until p(FETCH_WIDTH)) {
            when(io.enq(i).valid && bank === j.U) {
                banks(j)(row) := io.enq(i)
            }
        }
    }

    val WriteFireVec = io.enq.map(_.valid)
    val WriteCount = PopCount(WriteFireVec)
    tail := tail + WriteCount

    // read logic
    val readBank = head & ((1 << log2Ceil(p(FETCH_WIDTH))) - 1).U
    val readRow = head >> log2Ceil(p(FETCH_WIDTH)).U
    val readData = WireInit(0.U.asTypeOf(new L1BTBUpdateInfo))

    for(i <- 0 until p(FETCH_WIDTH)) {
        when(i.U === readBank) {
            readData := banks(i)(readRow)
        }
    }

    io.deq := readData

    io.rd_able := count =/= 0.U
    io.wr_able := count =/= size.U

    head := head + readData.valid
    count := count + WriteCount - readData.valid
}
