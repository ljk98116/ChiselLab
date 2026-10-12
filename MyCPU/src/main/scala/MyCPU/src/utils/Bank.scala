package MyCPU.utils

import chisel3._
import chisel3.util._
import MyCPU.io._

import org.chipsalliance.cde.config._
import chisel3.Module.clock

abstract class BaseBank[T <: Data](depth: Int, gen: T, name: String = ""){
    val idx_width = log2Ceil(depth)
    val data_width = gen.getWidth
}

class LargeBank[T <: Data](clock: Clock, reset: Reset, depth: Int, gen: T, name: String = "") extends BaseBank(depth, gen, name) {
    val impl = Module(new LUTRAMImpl(depth, gen, name))
    // default connect
    impl.io.en := true.B
    impl.io.raddr := 0.U
    impl.io.waddr := 0.U
    impl.io.wen := false.B
    impl.io.wdata := 0.U
    impl.io.mask := 0.U
    impl.clock := clock
    impl.reset := reset

    def read(addr: UInt): UInt = {
        impl.io.raddr := addr
        impl.io.mask := ~0.U(data_width.W)
        return impl.io.rdata
    }

    def write(addr: UInt, wmask: UInt, wdata: UInt): Unit = {
        impl.io.waddr := addr
        impl.io.wen := true.B
        impl.io.mask := wmask
        impl.io.wdata := wdata
    }
}

class LargeSyncBank[T <: Data](clock: Clock, reset: Reset, depth: Int, gen: T, name: String = "") extends BaseBank(depth, gen, name) {
    val impl = Module(new SRAMImpl(depth, gen, name))
    // default connect
    impl.io.en := true.B
    impl.io.raddr := 0.U
    impl.io.waddr := 0.U
    impl.io.wen := false.B
    impl.io.wdata := 0.U
    impl.io.mask := 0.U
    impl.clock := clock
    impl.reset := reset
    
    def read(addr: UInt): UInt = {
        impl.io.raddr := addr
        impl.io.mask := ~(0.U)(data_width.W)
        return impl.io.rdata
    }

    def write(addr: UInt, wmask: UInt, wdata: UInt): Unit = {
        impl.io.waddr := addr
        impl.io.wen := true.B
        impl.io.mask := wmask
        impl.io.wdata := wdata
    }
}