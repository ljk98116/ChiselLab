package MyCPU.middleend

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._
import org.chipsalliance.cde.config._
import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

// used for allocate a free LFST Table
// when full, just cover the oldest one
class LFSTFreeIdBuffer(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val alloc = Input(Bool())
        val freeId = Output(UInt(log2Ceil(p(LFST_SIZE)).W))
        val freeIdValid = Output(Bool())
    })

    val freeIdVec = RegInit(VecInit((0 until p(LFST_SIZE)).map(_.U(log2Ceil(p(LFST_SIZE)).W))))

    val head = RegInit(0.U(log2Ceil(p(LFST_SIZE)).W))

    when(io.alloc) {
        head := head + 1.U
        io.freeId := freeIdVec(head)
        io.freeIdValid := true.B
    }.otherwise {
        io.freeId := 0.U
        io.freeIdValid := false.B
    }
}
