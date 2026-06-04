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
        val stall_from_backpipe = Input(Bool())
        val mem3_enqs = Input(Vec(bankNum, new CacheRefillReq)) // from mem2
        val mem3_enqReady = Output(Bool())
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

    for(b <- 0 until bankNum) {
        canMerges(b) := Cat((0 until perBankSize).map(
            idx => banks(b)(idx).valid && io.mem3_enqs(b).valid &&
                banks(b)(idx).pa.GetPPN === io.mem3_enqs(b).pa.GetPPN &&
                banks(b)(idx).pa.bits(
                    p(L1CacheOffsetBits) + p(FETCH_OFFSET) + p(ICacheIndexBits) - 1, 
                    p(L1CacheOffsetBits) + p(FETCH_OFFSET)
                ) === io.mem3_enqs(b).pa.bits(
                    p(L1CacheOffsetBits) + p(FETCH_OFFSET) + p(ICacheIndexBits) - 1, 
                    p(L1CacheOffsetBits) + p(FETCH_OFFSET)                
                )
            )).orR
    }

    val canMergeCount = PopCount(canMerges)
    val writeCount = PopCount(io.mem3_enqs.map(_.valid))

    val headPtr = RegInit(0.U(ptrWidth.W))
    val tailPtr = RegInit(0.U(ptrWidth.W))

    val freeCount = RegInit(size.U((ptrWidth + 1).W))

    io.mem3_enqReady := !(freeCount < writeCount - canMergeCount)

    val mem3_enqsReform = WireInit(VecInit(
        Seq.fill(bankNum)(0.U.asTypeOf(new CacheRefillReq))
    ))

    val mem3_enqLocs = WireInit(VecInit(
        Seq.fill(bankNum)(0.U(bankBits.W))
    ))

    mem3_enqLocs(0) := 0.U
    for(i <- 1 until bankNum) {
        mem3_enqLocs(i) := PopCount((0 until i).map(j => {
            io.mem3_enqs(j).valid && !canMerges(j)
        }))
    }

    for(b <- 0 until bankNum) {
        when(io.mem3_enqs(b).valid && !canMerges(b)) {
            mem3_enqsReform(mem3_enqLocs(b)) := io.mem3_enqs(b)
        }
    }

    for(i <- 0 until bankNum) {
        val idx = tailPtr + i.U
        val bankIdx = idx(bankBits - 1, 0)
        val bankRow = idx(ptrWidth - 1, bankBits)
        when(mem3_enqsReform(i).valid) {
            banks(bankIdx)(bankRow) := mem3_enqsReform(i)
        }
    }

    io.deqEnable := headPtr =/= tailPtr

    val headBankId = headPtr(bankBits - 1, 0)
    val headBankRow = headPtr(ptrWidth - 1, bankBits)
    
    tailPtr := Mux(io.mem3_enqReady, tailPtr + writeCount - canMergeCount, tailPtr)
    io.deq := Mux(io.deqEnable, banks(headBankId)(headBankRow), 0.U.asTypeOf(new CacheRefillReq))
}

