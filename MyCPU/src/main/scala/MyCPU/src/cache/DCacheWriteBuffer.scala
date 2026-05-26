package MyCPU.cache

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._
import MyCPU.cache._

// write back to dram / L2 cache
// one by one write
// multi write, 1 read, use pipeline, first do mapping to enq items
// connect to refiller, do axi write
// not merge enqs, merge will be more complex
class DCacheWriteBuffer(size: Int)(implicit p: Parameters) extends Module {
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
        val enqs = Input(Vec(bankNum, new DCacheWriteBackReq))
        val enqReady = Output(Bool())
        val deq = Output(new DCacheWriteBackReq)
    })

    val s1_enqs = PipeReg(io.enqs, !io.enqReady, io.flush)
    // calculate prefix sum for all enqs
    val s1_prefix_sums = WireInit(VecInit(
        Seq.fill(bankNum)(0.U(bankBits.W))
    ))
    val s1_stMask = WireInit(VecInit(
        Seq.fill(bankNum)(false.B)
    ))
    val s1_enqRes = WireInit(VecInit(
        Seq.fill(bankNum)(0.U.asTypeOf(new DCacheWriteBackReq))
    ))
    for(i <- 0 until bankNum) {
        s1_stMask(i) := s1_enqs(i).valid
    }

    // parallel calculate
    s1_prefix_sums(0) := 0.U
    for(i <- 1 until bankNum) {
        s1_prefix_sums(i) := PopCount(s1_stMask.asUInt(i-1, 0))
    }

    for(i <- 0 until bankNum) {
        when(s1_enqs(i).valid) {
            s1_enqRes(s1_prefix_sums(i)) := s1_enqs(i)
        }
    }

    val s2_enqs = PipeReg(s1_enqRes, !io.enqReady, io.flush)

    val banks = Seq.fill(bankNum)(
        new LargeBank(perBankSize, new DCacheWriteBackReq)
    )

    val headPtr = RegInit(0.U(ptrWidth.W))
    val tailPtr = RegInit(0.U(ptrWidth.W))

    val writeCount = PopCount(s2_enqs.map(_.valid))
    val freeCount = RegInit(size.U((ptrWidth + 1).W))

    val headBankIdx = headPtr(bankBits - 1, 0)
    val headBankRow = headPtr(ptrWidth - 1, bankBits)

    val headItem = WireInit(0.U.asTypeOf(new DCacheWriteBackReq))
    
    io.enqReady := writeCount < freeCount
    freeCount := freeCount - writeCount + headItem.valid

    for(b <- 0 until bankNum) {
        banks(b).io.raddr := 0.U
        banks(b).io.wen := false.B
        banks(b).io.waddr := 0.U
        banks(b).io.wdata := 0.U
        banks(b).io.wen := false.B
        banks(b).io.wmask := 0.U
    }

    for(i <- 0 until bankNum) {
        val idx = tailPtr + i.U
        val bankIdx = idx(bankBits - 1, 0)
        val bankRow = idx(ptrWidth - 1, bankBits)
        for(b <- 0 until bankNum) {
            when(b.U === bankIdx && s2_enqs(i).valid) {
                banks(b).io.waddr := bankRow
                banks(b).io.wdata := s2_enqs(i)
                banks(b).io.wen := true.B
                banks(b).io.wmask := ~(0.U)(((new DCacheWriteBackReq).getWidth).W)
            }
        }
    }

    for(b <- 0 until bankNum) {
        when(b.U === headBankIdx) {
            banks(b).io.raddr := headBankRow
            headItem := Mux(
                headPtr === tailPtr, 
                0.U.asTypeOf(new DCacheWriteBackReq), 
                banks(b).io.rdata
            )
        }
    }
    
    io.deq := headItem
}

