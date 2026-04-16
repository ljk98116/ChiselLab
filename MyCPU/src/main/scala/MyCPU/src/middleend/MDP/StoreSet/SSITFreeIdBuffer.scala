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
class SSITFreeIdBuffer(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val alloc = Input(Bool())
        val freeId = Output(UInt(log2Ceil(p(SSIT_SIZE)).W))
    })

    val freeIdVec = RegInit(VecInit((0 until p(SSIT_SIZE)).map(_.U(log2Ceil(p(SSIT_SIZE)).W))))

    val head = RegInit(0.U(log2Ceil(p(SSIT_SIZE)).W))

    when(io.alloc) {
        head := head + 1.U
        io.freeId := freeIdVec(head)
    }.otherwise {
        io.freeId := 0.U
    }
}
