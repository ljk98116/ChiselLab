package MyCPU.backend

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

// enq at dispatch1 stage
// deq when load reach rob top
// used for load RAW harvard check, when store reached rob top and get agu res, store inst check load raw harvard, mark the inst
// when load commit, check this queue, generate LoadHarvard, use the first harvard item
// recv load agu Addr from LDU
// fetchWidth banks, equals with ROB

// 128 items
class InOrderLoadQueue(size: Int)(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val flush = Input(Bool())
        val stall_from_backpipe = Input(Bool())
        val ldu_uops = Input(Vec(p(FETCH_WIDTH), new uOp))
        val dispatch1_stall = Output(Bool())
        val LoadRobEnable = Input(Vec(p(FETCH_WIDTH), new LoadQueueEnable))
        val StoreAccess = Input(Vec(p(STU_NUM), new AGUResult))
        val loadHarvard = Output(new LoadRAWHarvardInfo)
        val robHeadPtr = Input(UInt(p(ROBID_WIDTH).W))
        val robTailPtr = Input(UInt(p(ROBID_WIDTH).W)) // used as rob loc and 
    })

    val ldu_uops = PipeReg(io.ldu_uops, io.stall_from_backpipe || io.dispatch1_stall, io.flush)
    val uop_banks = Seq.fill(p(FETCH_WIDTH))(
        SyncReadMem(size / p(FETCH_WIDTH), new uOp)
    )
    val harvardInfoBanks = Seq.fill(p(FETCH_WIDTH))(
        SyncReadMem(size / p(FETCH_WIDTH), new LoadRAWHarvardInfo)
    )
    val ptrWidth = log2Ceil(size)
    val bankBits = log2Ceil(p(FETCH_WIDTH))

    // enq stage write
    
}