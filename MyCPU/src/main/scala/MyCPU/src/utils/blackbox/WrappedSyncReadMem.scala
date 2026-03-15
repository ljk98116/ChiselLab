package MyCPU.utils

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._

class WrappedSyncReadMemCustom[T <: Data](depth: Int, gen: T, name: String = "")(implicit p:Parameters) {
    require(p(UseCustomLibraryConfig), "error, not use custom library config")
    val mem = SyncReadMem(depth, gen)

    def read(idx: UInt) = mem.read(idx)
    def write(idx: UInt, data: T) = mem.write(idx, data)
    def write(idx: UInt, data: T, mask: Seq[Bool])(implicit evidence: T <:< Vec[_]): Unit = mem.write(idx, data, mask)
}