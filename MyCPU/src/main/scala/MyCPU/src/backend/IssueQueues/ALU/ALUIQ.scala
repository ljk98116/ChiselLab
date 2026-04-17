package MyCPU.backend

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

// p(ALU_NUM) banks, 
// enq Stage choose from current enq Item and uOp skidBuffers, improve IPC, payload avg
// select stage: use last tick's age matrix and rdy state 
// recycle stage(regread stage): write back issued item id to ALUIQfreeIdBuffer
class ALUIQ(implicit p: Parameters) extends Module {
    val totalSize = p(ALUIQ_SIZE)
    val perBankSize = totalSize / p(ALU_NUM)
    val ptrWidth = log2Ceil(totalSize)
    val perBankPtrWith = log2Ceil(perBankSize)

    val freeIdBuffer = Module(new ALUIQFreeIdBuffer)

    val io = IO(new Bundle {
        val flush = Input(Bool())
        val stall_from_backpipe = Input(Bool())
        val alu_uops = Input(Vec(p(FETCH_WIDTH), new uOp))
        val issue0_stall = Output(Bool())
        // from LUs
        val aluResult = Input(Vec(p(ALU_NUM), new ALUResult))
        val memResult = Input(Vec(p(AGU_NUM), new MemResult))
        val sysResult = Input(new SysResult)
        // from LSQ select
        val aguQueueSelectRes = Input(Vec(p(AGU_NUM), new AGUIQEntry))
        
    })

    /// issue stage1: enq, choose from skidBuffers and current input(in case FETCH_WIDTH > ALU_NUM)
    val allocReq = VecInit(io.alu_uops.map(_.valid))
    val allocResp = freeIdBuffer.io.allocResp

    freeIdBuffer.io.allocReq := allocReq
    freeIdBuffer.io.flush := io.flush
    freeIdBuffer.io.stall_from_backpipe := io.stall_from_backpipe
    
    io.issue0_stall := freeIdBuffer.io.deqReady

    val skidBuffers = Seq.fill(p(ALU_NUM))(
        Module(new MultiPortQueueNW1R(p(FETCH_WIDTH), p(FETCH_WIDTH) / p(ALU_NUM), new uOp))
    )

    for(b <- 0 until p(ALU_NUM)) {

    }


}
