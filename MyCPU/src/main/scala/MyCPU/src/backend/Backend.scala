package MyCPU.backend

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._
import MyCPU.cache._

class Backend(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val flush = Input(Bool())
        val ldu_uops = Input(Vec(p(FETCH_WIDTH), new uOp))
        val stu_uops = Input(Vec(p(FETCH_WIDTH), new uOp))
        val alu_uops = Input(Vec(p(FETCH_WIDTH), new uOp))
        val sys_uops = Input(Vec(p(FETCH_WIDTH), new uOp))
        // to middle end
        val issue0_stall = Output(Bool())
        // dcache

        // load / store reached rob top / rob
        val loadQueueRobEnable = Input(Vec(p(FETCH_WIDTH), new LoadQueueEnable))
        val storeQueueRobEnable = Input(Vec(p(FETCH_WIDTH), new StoreQueueEnable))

    })

    val issueStages = Module(new IssueStages)
    val regReadStage = Module(new RegReadStage)
    val prf = Module(new PRF)
    val exStage = Module(new EXStage)
    val memStages = Module(new MemStages)
    val loadWbQueue = Module(new LoadWbQueue(p(DCacheMSHRNum)))
    val storeWbQueue = Module(new StoreWbQueue(p(DCacheMSHRNum)))

    val prfBankNum = p(ALU_NUM) + p(LDU_NUM) + p(SYS_ALU_NUM)
    val prfWbQueue = Module(new PRFWbQueue(prfBankNum * 4, prfBankNum))
    val loadBuffer = Module(new LoadBuffer(p(DCacheMSHRNum) * 4))
    val storeBuffer = Module(new StoreBuffer(p(DCacheMSHRNum) * 4))
    val commitStage = Module(new CommitStage)
    val ratCommit = Module(new RATCommit)
}