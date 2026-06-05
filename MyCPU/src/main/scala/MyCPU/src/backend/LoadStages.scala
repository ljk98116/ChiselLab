package MyCPU.backend

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

// if mdp is right, when rob enable a store, load issue after store, so load can use storebuffer's store aguResult
// if mdp is wrong, load can not use store's result(no need access storebuffer), 
// return an error result(checked when store before it reached rob head)

// mem0: 
// access dcache send aguRes to storeBuffer to check pipeline(virt addr check), if hit, dont care for dcache hit,
// enq to a mshrNum / 4 size buffer

// mshrNum / 4 buffer: judge refill resp pa and bank hit infos match, update state and data, send result to CDB
class LoadStages(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val flush = Input(Bool())
        val stall_from_backpipe = Input(Bool())
        val lduResult = Input(Vec(p(LDU_NUM), new AGUResult))
        val mem0_read_reqs = Output(Vec(p(LDU_NUM), new DCacheReq))
        // storebuffer resp
        val mem0_storeBufferResult = Input(Vec(p(LDU_NUM), new StoreBufferRespInfo))
        val mem0_ld_enqs = Output(Vec(p(LDU_NUM), new LDUResult))
    })

    val lduResult = PipeReg(io.lduResult, io.stall_from_backpipe, io.flush)
    for(i <- 0 until p(LDU_NUM)) {
        io.mem0_read_reqs(i).valid := lduResult(i).valid
        io.mem0_read_reqs(i).addr := lduResult(i).addr
        io.mem0_read_reqs(i).data := lduResult(i).wdata
        io.mem0_read_reqs(i).isSignExt := lduResult(i).is_sign_ext
        io.mem0_read_reqs(i).mask := lduResult(i).mask
        io.mem0_read_reqs(i).pipeId := (i + p(STU_NUM)).U
        io.mem0_read_reqs(i).robLoc := lduResult(i).robLoc
        io.mem0_read_reqs(i).wen := false.B
    }
}