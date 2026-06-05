package MyCPU.cache

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

// mshr queue
class ICacheMSHRQueue(size: Int)(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val enq = Input(new CacheRefillReq)
        val enqReady = Output(Bool())
        val deqValid = Input(Bool()) // deq enable
        val deq = Output(new CacheRefillReq)
        val deqReady = Output(Bool())
    })

    val bank = RegInit(VecInit(
        Seq.fill(size)(0.U.asTypeOf(new CacheRefillReq))
    ))

    val canMerge = Cat((0 until size).map(
        idx => bank(idx).valid && io.enq.valid &&
            bank(idx).pa.GetPPN === io.enq.pa.GetPPN &&
            bank(idx).pa.bits(
                p(L1CacheOffsetBits) + p(FETCH_OFFSET) + p(ICacheIndexBits) - 1, 
                p(L1CacheOffsetBits) + p(FETCH_OFFSET)
            ) === io.enq.pa.bits(
                p(L1CacheOffsetBits) + p(FETCH_OFFSET) + p(ICacheIndexBits) - 1, 
                p(L1CacheOffsetBits) + p(FETCH_OFFSET)                
            )
    )).orR

    val ptrWidth = log2Ceil(size)
    val headPtr = RegInit(0.U(ptrWidth.W))
    val tailPtr = RegInit(0.U(ptrWidth.W))

    val freeCount = RegInit(size.U((ptrWidth + 1).W))

    io.deqReady := !(freeCount === size.U) && bank(headPtr).valid && io.deqValid
    io.deq := Mux(io.deqValid, bank(headPtr), 0.U.asTypeOf(new CacheRefillReq))

    io.enqReady := (freeCount =/= 0.U) || (freeCount === 0.U && canMerge)

    when(io.enq.valid && freeCount =/= 0.U && !canMerge) {
        bank(tailPtr) := io.enq
        tailPtr := tailPtr + 1.U
        freeCount := freeCount - 1.U + io.deqReady
    }

    headPtr := headPtr + io.deqValid
}