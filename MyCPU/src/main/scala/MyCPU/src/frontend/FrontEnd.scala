package MyCPU.frontend

import chisel3._
import chisel3.util._

import MyCPU.config._
import MyCPU.io._
import MyCPU.frontend.BPU._
import MyCPU.frontend.BPU.predictors._
import org.chipsalliance.cde.config._

class FrontEnd(implicit p: Parameters) extends Module {
    // if0 stage: generate fetch pc, access L0 & L1 BTB
    // if0 stage: access ITLB
    // use l0 btb res & l1 btb res to generate next pred target
    // use final pred target to access RAS, piplined l0 pred result
    // l0 jump but l1 not jump, not save icache request, redirect to l1 pred target
    val io = IO(new Bundle{
        val flush = Input(Bool())
        val flush_pc = Input(UInt(p(ADDR_WIDTH).W))
        // from commit stage
        val btb_update = Input(Vec(p(FETCH_WIDTH), new L1BTBUpdateInfo))
        // to iTLB
        
    })

    val l0_btb = Module(new L0BTB)
    val l1_btb = Module(new L1BTB)
    val ras = Module(new RAS)

    val predictor: Predictor = Module(new GSharePredictor)

}
