package MyCPU.backend

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

// stage 0: send stu aguRes to storeBuffer / access dTLB / access dcache tagV
// stage 1: dcache get tagV
// stage 2: dcache judge hit and write data
class StoreStages(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {

    })
}