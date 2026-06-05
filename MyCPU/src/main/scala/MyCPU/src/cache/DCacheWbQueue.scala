package MyCPU.cache

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

// do not do merge, write to DRAM(need to opt)  
class DCacheWbQueue(size: Int)(implicit p: Parameters) extends Module {
    // sigle way sram ports
    val writePortNum = p(STU_NUM)
    val bankNum = writePortNum
    // use writePortNum as bankNum
    // copy readPortNum within a bank
    val bankBits = log2Ceil(bankNum)
    val perBankSize = size / bankNum
    val ptrWidth = log2Ceil(size)

    val io = IO(new Bundle {
        val flush = Input(Bool())
        val stall_from_backpipe = Input(Bool())
        // from mem4, when store hit / refill hit
        val mem4_wb_buffer_enq = Input(Vec(bankNum, new DCacheWriteBackReq))
        val mem4_wb_buffer_enqReady = Output(Bool())
        val deqEnable = Input(Bool())
        val deq = Output(new DCacheWriteBackReq)
    })

    val banks = Seq.fill(bankNum)(
        RegInit(VecInit(
            Seq.fill(perBankSize)(0.U.asTypeOf(new DCacheWriteBackReq))
        ))
    )

    val headPtr = RegInit(0.U(ptrWidth.W))
    val tailPtr = RegInit(0.U(ptrWidth.W))
    val freeCount = RegInit(size.U((ptrWidth + 1).W))

    val mem4_prefix_sums = WireInit(VecInit(
        Seq.fill(bankNum)(0.U(bankBits.W))
    ))

    val mem4_enqRes = WireInit(VecInit(
        Seq.fill(bankNum)(0.U.asTypeOf(new DCacheWriteBackReq))
    )) 

    mem4_prefix_sums(0) := 0.U
    for(i <- 1 until bankNum) {
        mem4_prefix_sums(i) := PopCount((0 until i - 1).map(j => io.mem4_wb_buffer_enq(j).valid))
    }

    for(i <- 0 until bankNum) {
        when(io.mem4_wb_buffer_enq(i).valid) {
            mem4_enqRes(mem4_prefix_sums(i)) := io.mem4_wb_buffer_enq(i)
        }
    }

    val writeCount = PopCount((0 until bankNum).map(i => mem4_enqRes(i).valid))

    io.mem4_wb_buffer_enqReady := writeCount < freeCount

    freeCount := Mux(io.mem4_wb_buffer_enqReady, freeCount - writeCount, freeCount)

    for(i <- 0 until bankNum) {
        val idx = tailPtr + i.U
        val bankId = idx(bankBits - 1, 0)
        val bankRow = idx(ptrWidth - 1, bankBits)
        for(b <- 0 until bankNum) {
            when(b.U === bankId && mem4_enqRes(i).valid) {
                banks(b)(bankRow) := mem4_enqRes(i)
            }
        }
    }

    val headBankId = headPtr(bankBits - 1, 0)
    val headBankRow = headPtr(ptrWidth - 1, bankBits)

    val canDeq = (headPtr =/= tailPtr) && io.deqEnable
    io.deq := 0.U.asTypeOf(new DCacheWriteBackReq)
    for(b <- 0 until bankNum) {
        when(b.U === headBankId && canDeq) {
            io.deq := banks(b)(headBankRow)
        }
    }

    headPtr := headPtr + canDeq.asUInt
    tailPtr := Mux(
        io.mem4_wb_buffer_enqReady, 
        tailPtr + writeCount, 
        tailPtr
    )
}