package MyCPU.cache

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

class DCacheMSHRQueue(size: Int)(implicit p: Parameters) extends Module {
    // sigle way sram ports
    val readPortNum = p(LDU_NUM)
    val writePortNum = p(STU_NUM)
    val bankNum = writePortNum + readPortNum
    // use writePortNum as bankNum
    // copy readPortNum within a bank
    val bankBits = log2Ceil(bankNum)
    val ptrWidth = log2Ceil(size)
    val perBankSize = size / bankNum

    val io = IO(new Bundle {
        val flush = Input(Bool())
        val mem2_enqs = Input(Vec(bankNum, new CacheRefillReq)) // from mem2
        val mem2_enqReady = Output(Bool())
        val deqEnable = Input(Bool())
        val deq = Output(new CacheRefillReq)        
    })

    val banks = RegInit(VecInit(
        Seq.fill(bankNum)(
            VecInit(Seq.fill(perBankSize)(0.U.asTypeOf(new CacheRefillReq)))
        )
    ))    

    val canMerges = WireInit(VecInit(
        Seq.fill(bankNum)(false.B)
    ))

    for(i <- 0 until bankNum) {
        canMerges(i) := Cat((0 until size).map(
            idx => banks(i)(idx).valid && io.mem2_enqs(i).valid &&
                banks(i)(idx).pa.GetPPN === io.mem2_enqs(i).pa.GetPPN &&
                banks(i)(idx).pa.bits(
                    p(L1CacheOffsetBits) + p(FETCH_OFFSET) + p(ICacheIndexBits) - 1, 
                    p(L1CacheOffsetBits) + p(FETCH_OFFSET)
                ) === io.mem2_enqs(i).pa.bits(
                    p(L1CacheOffsetBits) + p(FETCH_OFFSET) + p(ICacheIndexBits) - 1, 
                    p(L1CacheOffsetBits) + p(FETCH_OFFSET)                
                )
            )).orR
    }

    val canMergeCount = PopCount(canMerges)
    val writeCount = PopCount(io.mem2_enqs.map(_.valid))

    val headPtr = RegInit(0.U(ptrWidth.W))
    val tailPtr = RegInit(0.U(ptrWidth.W))

    val freeCount = RegInit(size.U((ptrWidth + 1).W))

    io.mem2_enqReady := !(freeCount < writeCount - canMergeCount)

    
}

