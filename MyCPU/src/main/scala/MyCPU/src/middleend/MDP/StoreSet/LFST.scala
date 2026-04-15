package MyCPU.middleend

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._
import org.chipsalliance.cde.config._
import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

// Regfile based LFST
// rename2 get LFST Index
// dispatch1 read LFST to get canSpecExec
class LFST(implicit p: Parameters) extends Module {

    require(p(SLOT_NUM) == 2, "LFST only support 2 slots now")

    val io = IO(new Bundle {
        val flush = Input(Bool())
        val stall_from_backpipe = Input(Bool())
        val rename2_ssitEntries = Input(Vec(p(FETCH_WIDTH), new SSITEntry))
        val dispatch1_pcs = Input(Vec(p(FETCH_WIDTH), UInt(p(ADDR_WIDTH).W)))
        val dispatch1_robIds = Input(Vec(p(FETCH_WIDTH), UInt(p(ROBID_WIDTH).W)))
        val dispatch1_isStoreVec = Input(Vec(p(FETCH_WIDTH), Bool()))
        val dispatch1_isLoadVec = Input(Vec(p(FETCH_WIDTH), Bool()))
        val dispatch1_canSpecExecVec = Output(Vec(p(FETCH_WIDTH), Bool()))
    })

    val lfst = RegInit(VecInit(
        Seq.fill(p(LFST_SIZE))(0.U.asTypeOf(new LFSTEntry))
    ))

    val rename2_lfstIndexVec = Wire(Vec(p(FETCH_WIDTH), UInt(log2Ceil(p(LFST_SIZE)).W)))
    for(i <- 0 until p(FETCH_WIDTH)) {
        val lfst_index = WireInit(0.U(log2Ceil(p(LFST_SIZE)).W))
        when(io.rename2_ssitEntries(i).valid) {
            switch(Cat(io.rename2_ssitEntries(i).LFSTValids(1), io.rename2_ssitEntries(i).LFSTValids(0))) {
                is("b01".U) { 
                    lfst_index := io.rename2_ssitEntries(i).LFSTIDs(0) 
                }
                is("b10".U) { 
                    lfst_index := io.rename2_ssitEntries(i).LFSTIDs(1) 
                }
                is("b11".U) { 
                    lfst_index := Mux(
                        io.rename2_ssitEntries(i).LFSTIDs(0) < io.rename2_ssitEntries(i).LFSTIDs(1), 
                        io.rename2_ssitEntries(i).LFSTIDs(0), 
                        io.rename2_ssitEntries(i).LFSTIDs(1)
                    ) 
                } // if both valid, just choose the small one
            }
        }
        rename2_lfstIndexVec(i) := lfst_index
    }

    val dispatch1_lfstIndexVec = PipeReg(rename2_lfstIndexVec, io.stall_from_backpipe, io.flush)

    for(i <- 0 until p(FETCH_WIDTH)) {
        val lfst_entry = lfst(dispatch1_lfstIndexVec(i))

        when(io.dispatch1_isLoadVec(i)) {
            io.dispatch1_canSpecExecVec(i) := 
                !(lfst_entry.valid) // valid must not spec exec, otherwise can spec exec
        }.otherwise {
            io.dispatch1_canSpecExecVec(i) := false.B
        }

        // store inst cover the latest LFST entry
        when(io.dispatch1_isStoreVec(i)) {
            lfst(dispatch1_lfstIndexVec(i)).valid := true.B
            lfst(dispatch1_lfstIndexVec(i)).pc := io.dispatch1_pcs(i)
            lfst(dispatch1_lfstIndexVec(i)).robId := io.dispatch1_robIds(i)
        }
    }
}
