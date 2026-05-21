package MyCPU.middleend

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._

import org.chipsalliance.cde.config._
import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

class LFSTAccessReq(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val isStore = Bool()
    val isLoad = Bool()
    val pc = UInt(p(ADDR_WIDTH).W)
}

class StoreSet(implicit p: Parameters) extends MDP {

    val ssit = Module(new SSIT)
    val lfst = Module(new LFST)
    val ssitFreeIdBuffer = Module(new SSITFreeIdBuffer)

    // rename1 read SSIT
    ssit.io.decode_pcs := io.decode_pcs
    ssit.io.decode_pcValids := io.decode_pcValids

    // wb1 update SSIT
    ssit.io.flush := io.flush
    ssit.io.rename1_stall := io.rename1_stall
    ssit.io.rename2_stall := io.rename2_stall
    ssit.io.stall_from_backpipe := io.stall_from_backpipe
    ssit.io.wb1_ssit_update <> io.loadRAWHarvardInfo

    ssitFreeIdBuffer.io.alloc := ssit.io.wb1_ssit_freeIdAllocReq
    ssit.io.wb1_ssit_freeIdAllocResp := ssitFreeIdBuffer.io.freeId
    ssit.io.rename1_isLoadVec := io.rename1_isLoadVec
    ssit.io.rename1_isStoreVec := io.rename1_isStoreVec

    // rename2 gen LFST read/write req for all lfsts

    lfst.io.flush := io.flush
    lfst.io.stall_from_backpipe := io.stall_from_backpipe
    lfst.io.rename2_stall := io.rename2_stall

    lfst.io.rename1_pcs := io.rename1_pcs
    lfst.io.rename1_isLoadVec := io.rename1_isLoadVec
    lfst.io.rename1_isStoreVec := io.rename1_isStoreVec
    lfst.io.rename1_robIds := io.rename1_robIds
    lfst.io.rename1_ssitEntries := ssit.io.rename1_LFSTEntries

    io.dispatch1_canSpecExec := lfst.io.dispatch1_canSpecExecVec
    io.dispatch1_blockStIds := lfst.io.dispatch1_blockStIds
}