package MyCPU.middleend

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._
import org.chipsalliance.cde.config._
import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

// Regfile based LFST
// rename1 try read slot0 value, (delay at rename2)
// rename2 try read slot1 value (delay at dispatch1)
// dispatch1 get canSpecExec
// store: if 2 storeSet, both write, if 1 store set write once
// load: just read both LFST to judge if can spec exec
//    slot0 wr slot1 wr
//             slot0 and slot1 read, use slot1 on write value
class LFST(implicit p: Parameters) extends Module {

    require(p(SLOT_NUM) == 2, "LFST only support 2 slots now")

    val io = IO(new Bundle {
        val flush = Input(Bool())
        val stall_from_backpipe = Input(Bool())
        val rename2_stall = Input(Bool())
        val rename1_ssitEntries = Input(Vec(p(FETCH_WIDTH), new SSITEntry))
        val rename1_pcs = Input(Vec(p(FETCH_WIDTH), UInt(p(ADDR_WIDTH).W)))
        val rename1_robIds = Input(Vec(p(FETCH_WIDTH), UInt(p(ROBID_WIDTH).W)))
        val rename1_isStoreVec = Input(Vec(p(FETCH_WIDTH), Bool()))
        val rename1_isLoadVec = Input(Vec(p(FETCH_WIDTH), Bool()))
        val dispatch1_canSpecExecVec = Output(Vec(p(FETCH_WIDTH), Bool()))
    })

    val bankNum = p(FETCH_WIDTH)
    val perBankSize = p(LFST_SIZE) / bankNum

    // 2 read port for slot0 and 1
    val lfst = Seq.fill(p(FETCH_WIDTH) * p(SLOT_NUM))(
        SyncReadMem(perBankSize, new LFSTEntry)
    )

    val rename2_slot0_canSpecExec = WireInit(VecInit(
        Seq.fill(p(FETCH_WIDTH))(false.B)
    ))

    val dispatch1_slot1_canSpecExec = WireInit(VecInit(
        Seq.fill(p(FETCH_WIDTH))(false.B)
    ))

    val rename2_ssitentries = PipeReg(io.rename1_ssitEntries, io.rename2_stall || io.stall_from_backpipe, io.flush)
    val rename2_isLoadVec = PipeReg(io.rename1_isLoadVec, io.rename2_stall || io.stall_from_backpipe, io.flush)
    val rename2_isStoreVec = PipeReg(io.rename1_isStoreVec, io.rename2_stall || io.stall_from_backpipe, io.flush)
    val rename2_pcs = PipeReg(io.rename1_pcs, io.rename2_stall || io.stall_from_backpipe, io.flush)
    val rename2_robIds = PipeReg(io.rename1_robIds, io.rename2_stall || io.stall_from_backpipe, io.flush)

    val dispatch1_isLoadVec = PipeReg(rename2_isLoadVec, io.stall_from_backpipe, io.flush)
    val dispatch1_slot0_canSpecExec = PipeReg(rename2_slot0_canSpecExec, io.stall_from_backpipe, io.flush)

    // rename1 write slot0
    // rename2 write slot1
    // rename1 read slot0
    // rename2 read slot1
    for(i <- 0 until p(FETCH_WIDTH)) {
        // slot0 need to write store inst 's pc
        when(io.rename1_ssitEntries(i).valid && io.rename1_ssitEntries(i).LFSTValids(0) && io.rename1_isStoreVec(i)) {
            val wdata = WireInit(0.U.asTypeOf(new LFSTEntry))
            wdata.pc := io.rename1_pcs(i)
            wdata.robId := io.rename1_robIds(i)
            wdata.valid := io.rename1_isStoreVec(i)
            lfst(2 * i).write(io.rename1_ssitEntries(i).LFSTIDs(0), wdata)
        }

        rename2_slot0_canSpecExec(i) := Mux(
            rename2_isLoadVec(i), 
            !lfst(i).read(io.rename1_ssitEntries(i).LFSTIDs(0)).valid, 
            true.B
        )

        when(rename2_ssitentries(i).valid && rename2_ssitentries(i).LFSTValids(1) && rename2_isStoreVec(i)) {
            val wdata = WireInit(0.U.asTypeOf(new LFSTEntry))
            wdata.pc := rename2_pcs(i)
            wdata.robId := rename2_robIds(i)
            wdata.valid := rename2_isStoreVec(i)
            lfst(i + p(FETCH_WIDTH)).write(rename2_ssitentries(i).LFSTIDs(1), wdata)
        }

        dispatch1_slot1_canSpecExec(i) := Mux(
            dispatch1_isLoadVec(i), 
            !lfst(i + p(FETCH_WIDTH)).read(rename2_ssitentries(i).LFSTIDs(1)).valid, 
            true.B
        )

        io.dispatch1_canSpecExecVec(i) := dispatch1_slot0_canSpecExec(i) && dispatch1_slot1_canSpecExec(i)
    }
}
