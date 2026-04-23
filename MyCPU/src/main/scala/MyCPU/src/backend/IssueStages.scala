package MyCPU.backend

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

// enq -> select(use last newest issue queue state)
// 178987
// 155098
// 147267
class IssueStages(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val flush = Input(Bool())
        val stall_from_backpipe = Input(Bool())
        val ldu_uops = Input(Vec(p(FETCH_WIDTH), new uOp))
        val stu_uops = Input(Vec(p(FETCH_WIDTH), new uOp))
        val alu_uops = Input(Vec(p(FETCH_WIDTH), new uOp))
        val sys_uops = Input(Vec(p(FETCH_WIDTH), new uOp))

        val issue0_stall = Output(Bool())
        val loadQueueRobEnable = Input(Vec(p(FETCH_WIDTH), new LoadQueueEnable))
        val storeQueueRobEnable = Input(Vec(p(FETCH_WIDTH), new StoreQueueEnable))
        val busyTableVec = Input(UInt((1 << p(PREG_WIDTH)).W))

        val ldu_items = Output(Vec(p(LDU_NUM), new LDUIQEntry))
        val stu_items = Output(Vec(p(STU_NUM), new STUIQEntry))
        val alu_items = Output(Vec(p(ALU_NUM), new ALUIQEntry))
        val sys_items = Output(Vec(p(SYS_ALU_NUM), new SYSIQEntry))
    })

    val alu_iq = Module(new ALUIQ)
    val ldu_iq = Module(new LoadQueue)
    val stu_iq = Module(new StoreQueue)
    val sys_iq = Module(new SYSIQ)

    alu_iq.io.flush := io.flush
    alu_iq.io.stall_from_backpipe := io.stall_from_backpipe
    alu_iq.io.busytableVec := io.busyTableVec
    alu_iq.io.alu_uops := PipeReg(io.alu_uops, io.stall_from_backpipe || io.issue0_stall, io.flush)

    ldu_iq.io.flush := io.flush
    ldu_iq.io.stall_from_backpipe := io.stall_from_backpipe
    ldu_iq.io.busytableVec := io.busyTableVec
    ldu_iq.io.ldu_uops := PipeReg(io.ldu_uops, io.stall_from_backpipe || io.issue0_stall, io.flush)
    ldu_iq.io.aluSelectRes := alu_iq.io.aluiq_o
    ldu_iq.io.robEnable := io.loadQueueRobEnable

    stu_iq.io.flush := io.flush
    stu_iq.io.stall_from_backpipe := io.stall_from_backpipe
    stu_iq.io.busyTableVec := io.busyTableVec
    stu_iq.io.store_uops := PipeReg(io.stu_uops, io.stall_from_backpipe || io.issue0_stall, io.flush)
    stu_iq.io.aluSelectRes := alu_iq.io.aluiq_o
    stu_iq.io.robEnable := io.storeQueueRobEnable

    sys_iq.io.flush := io.flush
    sys_iq.io.stall_from_backpipe := io.stall_from_backpipe
    sys_iq.io.busyTableVec := io.busyTableVec
    sys_iq.io.sys_uops := PipeReg(io.sys_uops,io.stall_from_backpipe || io.issue0_stall, io.flush)
    sys_iq.io.aluSelectRes := alu_iq.io.aluiq_o

    io.alu_items := alu_iq.io.aluiq_o
    io.ldu_items := ldu_iq.io.lduiq_o
    io.stu_items := stu_iq.io.issueItems
    io.sys_items := sys_iq.io.sysiq_o

    io.issue0_stall := 
        alu_iq.io.issue0_stall || ldu_iq.io.issue0_stall || 
        stu_iq.io.issue0_stall || sys_iq.io.issue0_stall
}