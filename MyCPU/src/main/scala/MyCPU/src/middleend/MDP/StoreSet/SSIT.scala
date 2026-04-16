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
        val rename1_stall = Input(Bool())
        val rename2_stall = Input(Bool())
        // commit io, banked write(only a load and a store)
        // alloc 1 SSIT ID for the update
        // write 1 SSIT entries loc, write to load and store idx of SSIT
        // alloc 1 LFST ID for the update
        // write the store inst pc and rob id into that LFST ID
        val wb1_ssit_update = Flipped(DecoupledIO(new LoadRAWHarvardInfo))
        // staged ssit update for commit, 2 SSIT entries are needed
        val wb1_ssit_freeIdAllocReq = Output(Bool())
        val wb1_ssit_freeIdAllocResp = Input(UInt(log2Ceil(p(SSIT_SIZE)).W))

        // decode get SSID for insts, use pc lowbits as bank id
        val decode_pcValids = Input(Vec(p(FETCH_WIDTH), Bool()))
        val decode_pcs = Input(Vec(p(FETCH_WIDTH), UInt(p(ADDR_WIDTH).W)))
        // rename1 use SSID entries to read/write LFST(lut ram impl)
        val rename1_isLoadVec = Input(Vec(p(FETCH_WIDTH), Bool()))
        val rename1_isStoreVec = Input(Vec(p(FETCH_WIDTH), Bool()))
        val rename1_LFSTEntries = Output(Vec(p(FETCH_WIDTH), new SSITEntry))
    })

    /// update SSIT process, total 4 cycles
    val storeUpdate::loadUpdate::Nil = Enum(2)
    // valid 1 cycle 2 reads
    val valid_array0 = RegInit(VecInit(Seq.fill(p(SSIT_SIZE))(false.B)))
    val valid_array1 = RegInit(VecInit(Seq.fill(p(SSIT_SIZE))(false.B)))
    // rename read can not have bank conflict
    // wbs read may have bank conflict, so first read load entry, then read store entry
    // use 4 Read 1Write banked SRAM to implement SSIT
    val ssid_array0 = Module(new MultiPortSRAMNR1W(indexBits, log2Ceil(p(LFST_SIZE)), p(FETCH_WIDTH)))
    val ssid_array1 = Module(new MultiPortSRAMNR1W(indexBits, log2Ceil(p(LFST_SIZE)), p(FETCH_WIDTH)))

    val SSITState = RegInit(loadUpdate)
    val nextSSITState = WireInit(SSITState)

    // wb1 read SSIT entry for load RAW harvard violation, get the SSIT Entry valid bit
    val wb1_loadEntryValid0 = io.wb1_ssit_update.valid && valid_array0(io.wb1_ssit_update.bits.loadPC(indexBits + offsetBits - 1, offsetBits))
    val wb1_storeEntryValid0 = io.wb1_ssit_update.valid && valid_array0(io.wb1_ssit_update.bits.storePC(indexBits + offsetBits - 1, offsetBits))
    val wb1_loadEntryValid1 = io.wb1_ssit_update.valid && valid_array1(io.wb1_ssit_update.bits.loadPC(indexBits + offsetBits - 1, offsetBits))
    val wb1_storeEntryValid1 = io.wb1_ssit_update.valid && valid_array1(io.wb1_ssit_update.bits.storePC(indexBits + offsetBits - 1, offsetBits))

    // alloc new storeset if load/store has harvard violation and both load/store not have SSIT entry
    io.wb1_ssit_freeIdAllocReq := io.wb1_ssit_update.valid && 
        !wb1_loadEntryValid0 && !wb1_storeEntryValid0 && 
        !wb1_loadEntryValid1 && !wb1_storeEntryValid1

    val wb2_loadEntryValid0 = RegNext(wb1_loadEntryValid0)
    val wb2_storeEntryValid0 = RegNext(wb1_storeEntryValid0)
    val wb2_loadEntryValid1 = RegNext(wb1_loadEntryValid1)
    val wb2_storeEntryValid1 = RegNext(wb1_storeEntryValid1)
    val wb2_ssit_freeIdAllocResp = RegNext(io.wb1_ssit_freeIdAllocResp)

    val wb2_ssit_freeIdAllocReq = RegNext(io.wb1_ssit_freeIdAllocReq)
    val wb2_ssit_update_bits = RegNext(io.wb1_ssit_update.bits)
    val wb2_ssit_update_valid = RegNext(io.wb1_ssit_update.valid)

    val wb2_loadEntrySSID0 = WireInit(0.U(log2Ceil(p(LFST_SIZE)).W))
    val wb2_loadEntrySSID1 = WireInit(0.U(log2Ceil(p(LFST_SIZE)).W))

    val wb2_minLoadEntrySSID = WireInit(0.U(log2Ceil(p(LFST_SIZE)).W))

    val wb2_updateFlag = WireInit(0.U(2.W))

    // wb1 freeIdAlloc, wb2 write loadPC, wb3 write storePC, setUpdate Ready
    // wb1 no freeIdAlloc, has store valid, no load, wb4 write loadPC with min(store SSID0, store SSID1) set updateReady
    // wb1 no freeIdAlloc, has load valid, no store, wb3 write storePC with min(load SSID0, load SSID1) set updateReady
    // wb1 no freeIdAlloc, has load valid, has store valid, wb4 write with min(load SSID0, load SSID1, store SSID0, store SSID1) to slot1 set updateReady
    switch(Cat(wb2_loadEntryValid1 || wb2_loadEntryValid0, wb2_storeEntryValid1 || wb2_storeEntryValid0)) {
        is("b00".U) {
            wb2_updateFlag := 0.U
        }
        is("b01".U) { 
            wb2_updateFlag := 1.U // only store has valid entry, update store entry
        }
        is("b10".U) { 
            wb2_updateFlag := 2.U // only load has valid entry, update load entry
        }
        is("b11".U) { 
            wb2_updateFlag := 3.U // both load and store have valid entry, update the smaller SSIT ID entry
        }
    }

    switch(Cat(wb2_loadEntryValid1, wb2_loadEntryValid0)) {
        is("b00".U) {
            wb2_minLoadEntrySSID := 0.U
        }
        is("b01".U) { 
            wb2_minLoadEntrySSID := wb2_loadEntrySSID0
        }
        is("b10".U) { 
            wb2_minLoadEntrySSID := wb2_loadEntrySSID1
        }
        is("b11".U) { 
            wb2_minLoadEntrySSID := Mux(wb2_loadEntrySSID0 < wb2_loadEntrySSID1, wb2_loadEntrySSID0, wb2_loadEntrySSID1)
        }
    }

    wb2_loadEntrySSID0 := Mux(SSITState === storeUpdate && wb2_ssit_update_valid, ssid_array0.io.readDatas(0), 0.U)
    wb2_loadEntrySSID1 := Mux(SSITState === storeUpdate && wb2_ssit_update_valid, ssid_array1.io.readDatas(0), 0.U)

    ssid_array0.io.writeEnable := false.B
    ssid_array0.io.writeAddr := 0.U
    ssid_array0.io.writeData := 0.U

    ssid_array1.io.writeEnable := false.B
    ssid_array1.io.writeAddr := 0.U
    ssid_array1.io.writeData := 0.U

    io.wb1_ssit_update.ready := false.B

    // case 0 write load at wb2
    when(wb2_updateFlag === 0.U) {
        ssid_array0.io.writeEnable := true.B
        ssid_array0.io.writeAddr := wb2_ssit_update_bits.loadPC(indexBits + offsetBits - 1, offsetBits)
        ssid_array0.io.writeData := wb2_ssit_freeIdAllocResp

        valid_array0(wb2_ssit_update_bits.loadPC(indexBits + offsetBits - 1, offsetBits)) := true.B
    }

    val wb3_loadEntryValid0 = RegNext(wb2_loadEntryValid0)
    val wb3_storeEntryValid0 = RegNext(wb2_storeEntryValid0)
    val wb3_loadEntryValid1 = RegNext(wb2_loadEntryValid1)
    val wb3_storeEntryValid1 = RegNext(wb2_storeEntryValid1)

    val wb3_storeEntrySSID0 = WireInit(0.U(log2Ceil(p(LFST_SIZE)).W))
    val wb3_storeEntrySSID1 = WireInit(0.U(log2Ceil(p(LFST_SIZE)).W))
    val wb3_minStoreEntrySSID = WireInit(0.U(log2Ceil(p(LFST_SIZE)).W))
    val wb3_minLoadEntrySSID = RegNext(wb2_minLoadEntrySSID)
    val wb3_updateFlag = RegNext(wb2_updateFlag)
    val wb3_ssit_update_valid = RegNext(wb2_ssit_update_valid)
    val wb3_ssit_update_bits = RegNext(wb2_ssit_update_bits)
    val wb3_ssit_freeIdAllocResp = RegNext(wb2_ssit_freeIdAllocResp)

    switch(Cat(wb3_storeEntryValid1, wb3_storeEntryValid0)) {
        is("b00".U) {
            wb3_minStoreEntrySSID := 0.U
        }
        is("b01".U) { 
            wb3_minStoreEntrySSID := wb3_storeEntrySSID0
        }
        is("b10".U) { 
            wb3_minStoreEntrySSID := wb3_storeEntrySSID1
        }
        is("b11".U) { 
            wb3_minStoreEntrySSID := Mux(wb3_storeEntrySSID0 < wb3_storeEntrySSID1, wb3_storeEntrySSID0, wb3_storeEntrySSID1)
        }
    }

    wb3_storeEntrySSID0 := Mux(SSITState === loadUpdate && wb3_ssit_update_valid, ssid_array0.io.readDatas(0), 0.U)
    wb3_storeEntrySSID1 := Mux(SSITState === loadUpdate && wb3_ssit_update_valid, ssid_array1.io.readDatas(0), 0.U)

    // case 0 and case 2 write store at wb3
    when(!wb3_updateFlag(0)) {
        ssid_array0.io.writeEnable := true.B
        ssid_array0.io.writeAddr := wb3_ssit_update_bits.storePC(indexBits + offsetBits - 1, offsetBits)
        ssid_array0.io.writeData := wb3_ssit_freeIdAllocResp

        valid_array0(wb3_ssit_update_bits.storePC(indexBits + offsetBits - 1, offsetBits)) := true.B
        io.wb1_ssit_update.ready := true.B
    }

    // first read load then read store
    when(SSITState === loadUpdate && io.wb1_ssit_update.valid) {
        ssid_array0.io.readAddrs(0) := io.wb1_ssit_update.bits.loadPC(indexBits + offsetBits - 1, offsetBits)
        ssid_array1.io.readAddrs(0) := io.wb1_ssit_update.bits.loadPC(indexBits + offsetBits - 1, offsetBits)
    }.elsewhen(SSITState === storeUpdate && wb2_ssit_update_valid) {
        ssid_array0.io.readAddrs(0) := io.wb1_ssit_update.bits.storePC(indexBits + offsetBits - 1, offsetBits)
        ssid_array1.io.readAddrs(0) := io.wb1_ssit_update.bits.storePC(indexBits + offsetBits - 1, offsetBits)
    }.otherwise {
        ssid_array0.io.readAddrs(0) := 0.U
        ssid_array1.io.readAddrs(0) := 0.U
    }

    when(SSITState === loadUpdate && io.wb1_ssit_update.valid) {
        SSITState := storeUpdate
    }.elsewhen(SSITState === storeUpdate && wb2_ssit_update_valid) {
        SSITState := loadUpdate
    }

    val wb4_loadEntryValid0 = RegNext(wb3_loadEntryValid0)
    val wb4_storeEntryValid0 = RegNext(wb3_storeEntryValid0)
    val wb4_loadEntryValid1 = RegNext(wb3_loadEntryValid1)
    val wb4_storeEntryValid1 = RegNext(wb3_storeEntryValid1)
    val wb4_minLoadEntrySSID = RegNext(wb3_minLoadEntrySSID)
    val wb4_minStoreEntrySSID = RegNext(wb3_minStoreEntrySSID)
    val wb4_updateFlag = RegNext(wb3_updateFlag)
    val wb4_ssit_update_valid = RegNext(wb3_ssit_update_valid)
    val wb4_ssit_update_bits = RegNext(wb3_ssit_update_bits)

    // use smaller SSID to update another SSIT entry
    val useStoreSSIDUpdateLoad = wb4_minLoadEntrySSID > wb4_minStoreEntrySSID
    val storeUseSlot0 = wb4_storeEntryValid1
    val loadUseSlot0 = wb4_loadEntryValid1
    val UseSlot0 = (useStoreSSIDUpdateLoad && storeUseSlot0)|| (!useStoreSSIDUpdateLoad && loadUseSlot0)
    val final_ssid = Mux(useStoreSSIDUpdateLoad, wb4_minStoreEntrySSID, wb4_minLoadEntrySSID)
    val final_pc = Mux(useStoreSSIDUpdateLoad, wb4_ssit_update_bits.loadPC, wb4_ssit_update_bits.storePC)

    // write load PC with min store SSID
    when(wb4_updateFlag(0)) {
        when(wb4_updateFlag(1)) {
            when(UseSlot0) {
                valid_array0(final_pc(indexBits + offsetBits - 1, offsetBits)) := true.B
            }
            ssid_array0.io.writeEnable := UseSlot0
            ssid_array0.io.writeAddr := final_pc(indexBits + offsetBits - 1, offsetBits)
            ssid_array0.io.writeData := final_ssid

            ssid_array1.io.writeEnable := !UseSlot0
            ssid_array1.io.writeAddr := final_pc(indexBits + offsetBits - 1, offsetBits)
            ssid_array1.io.writeData := final_ssid

            when(!UseSlot0) {
                valid_array1(final_pc(indexBits + offsetBits - 1, offsetBits)) := true.B
            }

        }.otherwise {
            ssid_array0.io.writeEnable := true.B
            ssid_array0.io.writeAddr := wb4_ssit_update_bits.loadPC(indexBits + offsetBits - 1, offsetBits)
            ssid_array0.io.writeData := wb4_minStoreEntrySSID

            valid_array0(wb4_ssit_update_bits.loadPC(indexBits + offsetBits - 1, offsetBits)) := true.B
        }
        io.wb1_ssit_update.ready := true.B
    }

    val rename1_pcValids = PipeReg(io.decode_pcValids, io.stall_from_backpipe || io.rename2_stall || io.rename1_stall, io.flush)
    // fetch access SSIT, directly read from SRAM
    for(slot <- 0 until p(SLOT_NUM)) {
        for(i <- 0 until p(FETCH_WIDTH)) {
            io.rename1_LFSTEntries(i) := 0.U.asTypeOf(new SSITEntry)
            val bankId = io.decode_pcs(i)(bankBits + offsetBits - 1, offsetBits) // use pc low bits as bank id
            if(slot == 0) {
                ssid_array0.io.readAddrs(i) := io.decode_pcs(i)(indexBits + offsetBits - 1, offsetBits)
                io.rename1_LFSTEntries(i).LFSTValids(slot) := 
                    valid_array0(io.decode_pcs(i)(indexBits + offsetBits - 1, offsetBits))
                io.rename1_LFSTEntries(i).LFSTIDs(slot) := ssid_array0.io.readDatas(i)
            }
            else {
                ssid_array1.io.readAddrs(i) := io.decode_pcs(i)(indexBits + offsetBits - 1, offsetBits)
                io.rename1_LFSTEntries(i).LFSTValids(slot) := 
                    valid_array1(io.decode_pcs(i)(indexBits + offsetBits - 1, offsetBits))
                io.rename1_LFSTEntries(i).LFSTIDs(slot) := ssid_array1.io.readDatas(i)
            }
            io.rename1_LFSTEntries(i).valid := io.rename1_isLoadVec(i) || io.rename1_isStoreVec(i)
        }
    }


}