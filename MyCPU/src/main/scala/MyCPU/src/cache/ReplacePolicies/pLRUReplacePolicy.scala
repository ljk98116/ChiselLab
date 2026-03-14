package MyCPU.cache

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

// treepLRU
// read victim idxBits cycles
// update victim idxBits cycles
class pLRUReplacePolicy(nPort: Int, nWayNum: Int)(implicit p: Parameters) 
extends ReplacePolicy(nPort, nWayNum) {
    require(p(UsepLRUPolicy), "pLRUPolicy not set")
    require(isPow2(nWayNum))
    require(nWayNum >= 2 && nWayNum <= 32)

    val idxBits = log2Ceil(nWayNum)
    val treeBitsN = nWayNum - 1
    val cycles = idxBits

    val tree = RegInit(VecInit(
        Seq.fill(treeBitsN)(false.B)
    ))

    var readIdxs = WireInit(VecInit(
        Seq.fill(idxBits)(0.U(log2Ceil(treeBitsN).W))
    ))
    val victim = WireInit(VecInit(
        Seq.fill(idxBits)(false.B)
    ))
    
    for(i <- 0 until idxBits) {
        victim(idxBits - i - 1) := tree(readIdxs(i))
        if(i < idxBits - 1) {
            readIdxs(i + 1) := Mux(tree(readIdxs(i)), readIdxs(i) << 1.U + 1.U, readIdxs(i) << 1.U)
        }
    }
    io.refillResp.valid := io.refillReq.valid
    io.refillResp.way := victim.asUInt

    var updateIdxs = WireInit(VecInit(
        Seq.fill(idxBits)(0.U(log2Ceil(treeBitsN).W))
    ))

    for(i <- 0 until idxBits) {
        tree(updateIdxs(i)) := ~tree(updateIdxs(i))
        val hitUpdateMap = 
            (Cat(io.hitUpdate.map(_.valid)) & Cat(io.hitUpdate.map(_.way(i)))).xorR ^
            (io.refillUpdate.valid & io.refillUpdate.way(i))
        if(i < idxBits - 1) {
            updateIdxs(i+1) := (updateIdxs(i) << 1.U) + hitUpdateMap
        }
    }
}

