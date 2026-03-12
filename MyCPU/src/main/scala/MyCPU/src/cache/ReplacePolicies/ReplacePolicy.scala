package MyCPU.cache

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

// refill update > hitUpdate, same cycle only do refill update
abstract class ReplacePolicy(nPort: Int, nWayNum: Int, depth: Int)(implicit p: Parameters) extends Module {
    val indexBits = log2Ceil(depth)
    val io = IO(new Bundle {
        val hitUpdate = Input(Vec(nPort, new ReplacePolicyHitUpdateInfo))
        val refillUpdate = Input(new ReplacePolicyRefillUpdateInfo)
        val refillReq = Input(new ReplacePolicyRefillReqInfo)
        val refillResp = Output(new ReplacePolicyRefillRespInfo)
    })
}

