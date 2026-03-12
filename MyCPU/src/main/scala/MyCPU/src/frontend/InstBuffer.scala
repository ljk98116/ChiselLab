package MyCPU.frontend

import chisel3._ 
import chisel3.util._ 
import MyCPU.config._ 
import MyCPU.io._ 
import MyCPU.utils._
import org.chipsalliance.cde.config._

// if1 enq, deq before decode, used as a pipelinestage
// has a valid pc, then enqValid is true
// check inst state, if icache miss, then wait refiller to inject insts
class InstBuffer(size: Int)(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val flush = Input(Bool())
        val stall = Input(Bool())
        // from if1
        val if1_pc_i = Input(Vec(p(FETCH_WIDTH), UInt(p(ADDR_WIDTH).W)))
        val if1_inst_i = Input(Vec(p(FETCH_WIDTH), UInt(p(DATA_WIDTH).W)))
        val if1_instValid_i = Input(Vec(p(FETCH_WIDTH), Bool()))
        // from if1 icache related
        val if1_pa_i = Input(new PhysicalAddress)
        val if1_icacheState_i = Input(Bool())

        val inst_o = Output(Vec(p(FETCH_WIDTH), UInt(p(DATA_WIDTH).W)))
        val instValid_o = Output(Vec(p(FETCH_WIDTH), Bool()))
    })

    
}
