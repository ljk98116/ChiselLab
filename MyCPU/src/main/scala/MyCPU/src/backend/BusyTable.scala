package MyCPU.backend

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

// update when exec/mem read done
// read at issueEnq Stage, directlt return the vec
class BusyTable(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val freeRegIdBufferResp = Input(Vec(p(FETCH_WIDTH), new FreeRegIdBufferReadResp))
        val aluResultResp = Input(Vec(p(ALU_NUM), new ALUResult))
        val memResultResp = Input(Vec(p(AGU_NUM), new MemResult))
        val sysResultResp = Input(new SysResult)
    })

    val busyTable = RegInit(0.U((1 << p(PREG_WIDTH)).W))

    val busyTableAfterRenameVec = Seq.fill(p(FETCH_WIDTH))(
        WireInit(0.U((1 << p(PREG_WIDTH)).W))
    )
    
    for(i <- 0 until p(FETCH_WIDTH)) {
        busyTableAfterRenameVec(i) := busyTable | (io.freeRegIdBufferResp(i).valid << io.freeRegIdBufferResp(i).preg)
    }

    val busyTableAfterExecVec = Seq.fill(p(ALU_NUM) + p(SYS_ALU_NUM))(0.U((1 << p(PREG_WIDTH)).W))
    for(i <- 0 until p(ALU_NUM)) {
        busyTableAfterExecVec(i) := busyTable | (io.aluResultResp(i).HasRd << io.aluResultResp(i).pd)
    }

    busyTableAfterExecVec(p(ALU_NUM)) := busyTable | (io.sysResultResp.HasRd << io.sysResultResp.pd)

    val busyTableAfterMemOpVec = Seq.fill(p(AGU_NUM))(0.U((1 << p(PREG_WIDTH)).W))

    for(i <- 0 until p(AGU_NUM)) {
        busyTableAfterMemOpVec(i) := busyTable | (io.memResultResp(i).HasRd << io.memResultResp(i).pd)
    }

    busyTable := 
        busyTableAfterRenameVec.reduce(_ | _) | 
        busyTableAfterExecVec.reduce(_ | _) |
        busyTableAfterMemOpVec.reduce(_ | _)
}