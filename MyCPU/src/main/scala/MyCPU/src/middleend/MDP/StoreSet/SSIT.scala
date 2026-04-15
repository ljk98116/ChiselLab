package MyCPU.middleend

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._

import org.chipsalliance.cde.config._
import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

// multi bank implement, only pipeline flush write, so need wb queue for banked write
// LFST only update when fetched a store inst
// SSIT only update when commit a load RAW harvard violation
// ld1 st1 harvard -> 
//    SSIT              LFST
// ld1 SSID1 true    SSID1 -> empty
// st1 SSID1 true
// 
// st1 fetch ->
//    SSIT              LFST
// ld1 SSID1 true    SSID1 -> st1's pc and robId
// st1 SSID1 true
// 
// ld1 fetch -> ld1 can not spec exec, because of st1's pc and robId in LFST
//    SSIT              LFST
// ld1 SSID1 true    SSID1 -> st1's pc and robId
// st1 SSID1 true
// 
// ld1 st2 harvard -> get ld1 SSID1 -> st2 use SSID1
//    SSIT              LFST
// ld1 SSID1 true    SSID1 -> st1's pc and robId
// st1 SSID1 true
// st2 SSID1 true
// 
// st2 fetch -> LFST SSID1 has value -> update LFST SSID1 with st2's pc and robId
//    SSIT              LFST
// ld1 SSID1 true    SSID1 -> st2's pc and robId
// st1 SSID1 true
// st2 SSID1 true
// 
// when store has StoreSet, but load not has
// ld2 st1 harvard -> set ld2 SSID1, because st1 has SSID
//    SSIT              LFST
// ld1 SSID1 true    SSID1 -> st2's pc and robId
// st1 SSID1 true
// st2 SSID1 true
// ld2 SSID1 true
// 
// when load has storeset, but store not have
// ld1 st3 harvard -> set st3 SSID1, because ld1 has SSID
//    SSIT              LFST
// ld1 SSID1 true    SSID1 -> st2's pc and robId
// st1 SSID1 true
// st2 SSID1 true
// ld2 SSID1 true
// st3 SSID1 true
//
// i.e. current state
//    SSIT              LFST
// ld1 SSID1 true    SSID1 -> st2's pc and robId
// st1 SSID1 true
// st2 SSID1 true
// ld2 SSID2 true
// st4 SSID2 true

// when ld2 st2 harvard -> set min(SSID1, SSID2) as SSID, allocate to another one
//    SSIT              LFST
// ld1 SSID1 true    SSID1 -> st2's pc and robId
// st1 SSID1 true    SSID2 -> st4's pc and robId
// st2 SSID1 true
// ld2 SSID2 true
// st4 SSID2 true
// 
// assume SSID1 < SSID2, so st2 and ld1 share SSID1
//    SSIT              LFST
// ld1 SSID1 true    SSID1 -> st2's pc and robId
// st1 SSID1 true    SSID2 -> st4's pc and robId
// st2 SSID1 true
// ld2 SSID1 true
// st4 SSID2 true
// 
// assume SSID1 > SSID2, so st2 and ld1 share SSID2
//    SSIT              LFST
// ld1 SSID1 true    SSID1 -> st2's pc and robId
// st1 SSID1 true    SSID2 -> st4's pc and robId
// st2 SSID2 true
// ld2 SSID2 true
// st4 SSID2 true

// fetch_width read
// 2 write(load SSIT and Store SSIT)
class SSIT(implicit p: Parameters) extends Module {

    val offsetBits = p(FETCH_OFFSET)
    val bankBits = log2Ceil(p(FETCH_WIDTH))
    val indexBits = log2Ceil(p(SSIT_SIZE))
    val totalSize = p(SSIT_SIZE)
    val perBankSize = totalSize / p(FETCH_WIDTH)

    val io = IO(new Bundle {
        val flush = Input(Bool())
        val stall_from_backpipe = Input(Bool())
        // commit io, banked write(only a load and a store)
        // alloc 1 SSIT ID for the update
        // write 1 SSIT entries loc, write to load and store idx of SSIT
        // alloc 1 LFST ID for the update
        // write the store inst pc and rob id into that LFST ID
        val wb1_ssit_update = Input(new SSITWriteReq)
        // staged ssit update for commit, 2 SSIT entries are needed
        val wb2_ssit_freeIdAllocReq = Output(Bool())
        val wb2_ssit_freeIdAllocResp = Input(UInt(log2Ceil(p(SSIT_SIZE)).W))

        // rename1 get SSID for insts, use pc lowbits as bank id
        val rename1_pcs = Input(Vec(p(FETCH_WIDTH), UInt(p(ADDR_WIDTH).W)))
        val rename1_isLoadVec = Input(Vec(p(FETCH_WIDTH), Bool()))
        val rename1_isStoreVec = Input(Vec(p(FETCH_WIDTH), Bool()))
        // rename2 use SSID entries to read/write LFST(lut ram impl)
        val rename2_LFSTEntries = Output(Vec(p(FETCH_WIDTH), new SSITEntry))
        val rename2_isLoadVec = Output(Vec(p(FETCH_WIDTH), Bool()))
        val rename2_isStoreVec = Output(Vec(p(FETCH_WIDTH), Bool()))
    })


}