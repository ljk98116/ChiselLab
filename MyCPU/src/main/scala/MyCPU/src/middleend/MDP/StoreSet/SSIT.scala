package MyCPU.middleend

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._

import org.chipsalliance.cde.config._
import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

// multi bank implement, only pipeline flush write, so need wb queue for banked write
class SSIT(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        // commit io, banked write(only a load and a store, but may have bank conflict)
        val ssit_update = Input(Vec(p(FETCH_WIDTH), new SSITWriteReq))
        // rename1 get SSID for insts, use pc lowbits as bank id
        val rename1_pcs = Input(Vec(p(FETCH_WIDTH), UInt(p(ADDR_WIDTH).W)))
        val isLoadVec = Input(Vec(p(FETCH_WIDTH), Bool()))
        val isStoreVec = Input(Vec(p(FETCH_WIDTH), Bool()))
        val LFSTEntries = Output(Vec(p(FETCH_WIDTH), new SSITEntry))
    })

    val offsetBits = p(FETCH_OFFSET)
    val bankBits = log2Ceil(p(FETCH_WIDTH))
    val indexBits = log2Ceil(p(SSIT_SIZE))
    val totalSize = p(SSIT_SIZE)
    val perBankSize = totalSize / p(FETCH_WIDTH)

    
}