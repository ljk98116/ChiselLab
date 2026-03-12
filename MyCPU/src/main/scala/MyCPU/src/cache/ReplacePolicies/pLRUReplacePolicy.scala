package MyCPU.cache

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

// priority: refill update > hit update
// need to handle bank conflict
class pLRUReplacePolicy(nPort: Int, nWayNum: Int, depth: Int)(implicit p: Parameters) 
extends ReplacePolicy(nPort, nWayNum, depth) {
    require(p(UsepLRUPolicy), "pLRUPolicy not set")
    def pLRUBits: Int = {
        return nWayNum - 1
    }

    val bankBits = log2Ceil(nPort)
    val banks = Seq.fill(nPort)(
        SyncReadMem(depth / nPort, UInt(pLRUBits.W))
    )

    val queues = Seq.fill(nPort)(
        Module(new MultiPortQueueNW1R(4, nWayNum, new ReplacePolicyHitUpdateInfo))
    )
    
        
}

