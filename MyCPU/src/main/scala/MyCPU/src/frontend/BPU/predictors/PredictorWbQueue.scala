package MyCPU.frontend.BPU

import chisel3._ 
import chisel3.util._ 
import MyCPU.config._ 
import MyCPU.io._ 
import MyCPU.utils._
import org.chipsalliance.cde.config._

// wb stage1: calculate rob item 's bank id, gen select vec
// wb stage2: generate prefix vec
// wb stage3: select deq rob item, enq dispatched rob items
// wb stage4: write into skid buffer

class PredictorWbQueue(size: Int)(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val enq = Input(Vec(p(FETCH_WIDTH), new PredictorUpdateInfo))
        val deq = Output(Vec(p(FETCH_WIDTH), new PredictorUpdateInfo))
        val enqReady = Output(Bool()) // wr_able
        val deqReady = Output(Vec(p(FETCH_WIDTH), Bool())) // maybe useless        
    })

    def compactPrefix(sel: Vec[Bool]): Vec[UInt] = {
        val n = sel.length
        val w = log2Ceil(n + 1)
        val result = Wire(Vec(n, UInt(w.W)))

        result(0) := 0.U
        for (i <- 1 until n) {
            result(i) := result(i-1) + sel(i-1).asUInt
        }
        result
    }    

    
}