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
    val lfstNum = p(SSIT_SIZE) * p(SLOT_NUM)

    val ssit = Module(new SSIT)

    val lfstIdBuffer = Module(new LFSTFreeIdBuffer)

    val lfstTable = Seq.fill(lfstNum)(
        Module(new LFST)
    )

    // rename1 read SSIT
    ssit.io.rename1_isLoadVec := io.rename1_isLoadVec
    ssit.io.rename1_isStoreVec := io.rename1_isStoreVec
    ssit.io.rename1_pcs := io.rename1_pcVec

    // rename2 gen LFST read/write req for all lfsts
    val rename2_ssitEntries = ssit.io.rename2_LFSTEntries

    
}