package MyCPU.backend

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

// prepare   enq       select      regread                     ex                                       wb
// prepare   enq                    select        regread(ex + judge ps1/ps2 == pd ?)                   ex

class RegReadStage(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val flush = Input(Bool())
        val stall_from_backpipe = Input(Bool())
        // read queue full then stall
        val regread_stall = Output(Bool())
        val ldu_items = Input(Vec(p(LDU_NUM), new LDUIQEntry))
        val stu_items = Input(Vec(p(STU_NUM), new STUIQEntry))
        val alu_items = Input(Vec(p(ALU_NUM), new ALUIQEntry))       
        // from exu/mem, current calculate res, used for prewakeup uOps
        val aluResult = Input(Vec(p(ALU_NUM), new ALUResult))
        val lduResult = Input(Vec(p(LDU_NUM), new LDUResult))

        val alu_items_ready = Output(Vec(p(ALU_NUM), new ALUIQEntry))
        val ldu_items_ready = Output(Vec(p(LDU_NUM), new LDUIQEntry))
        val stu_items_ready = Output(Vec(p(STU_NUM), new STUIQEntry))  
    })

    val ldu_items = PipeReg(io.ldu_items, io.stall_from_backpipe || io.regread_stall, io.flush)
    val stu_items = PipeReg(io.stu_items, io.stall_from_backpipe || io.regread_stall, io.flush)
    val alu_items = PipeReg(io.alu_items, io.stall_from_backpipe || io.regread_stall, io.flush)

    
}