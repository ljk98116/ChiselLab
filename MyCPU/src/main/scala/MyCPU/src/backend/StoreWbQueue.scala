package MyCPU.backend

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._
import MyCPU.cache._

class StoreWbQueue(size: Int)(implicit p: Parameters) extends Module {
    val bankNum = p(STU_NUM)
    val bankBits = log2Ceil(bankNum)
    val ptrWidth = log2Ceil(size)
    val perBankSize = size / bankNum
    val rowPtrWidth = log2Ceil(perBankSize)

    val io = IO(new Bundle {
        val flush = Input(Bool())
        val mem4_stu_result = Input(Vec(p(STU_NUM), new StoreWbQueueItem))
        val mem4_stu_deqs = Output(Vec(p(STU_NUM), new StoreWbQueueItem))
        val dcache_refill_resp = Input(new DCacheRefillResp)
        val mem4_stu_wb_stall = Output(Bool())
    })

    val banks = Seq.fill(bankNum)(
        RegInit(VecInit(
            Seq.fill(perBankSize)(0.U.asTypeOf(new StoreWbQueueItem))
        ))
    )

    val headPtr = RegInit(0.U(rowPtrWidth.W))
    val tailPtr = RegInit(0.U(rowPtrWidth.W))
    val freeCount = RegInit(perBankSize.U((rowPtrWidth + 1).W))

    val canDeq = VecInit((0 until bankNum).map(i => 
        (banks(i)(headPtr).valid && banks(i)(headPtr).hit) || !banks(i)(headPtr).valid
    )).asUInt.andR && headPtr =/= tailPtr 

    val enqCount = PopCount(io.mem4_stu_result.map(_.valid))
    val canEnq = freeCount >= enqCount

    val mem5_stu_results = PipeReg(io.mem4_stu_result, false.B, io.flush)
    val mem5_stu_valid_mask = VecInit(mem5_stu_results.map(_.valid))
    val mem5_stu_prefix_sums = WireInit(VecInit(Seq.fill(p(STU_NUM))(0.U(bankBits.W))))

    val mem5_stu_enqs = WireInit(VecInit(
        Seq.fill(p(STU_NUM))(0.U.asTypeOf(new StoreWbQueueItem))
    ))

    mem5_stu_prefix_sums(0) := 0.U
    for (i <- 1 until p(STU_NUM)) {
        mem5_stu_prefix_sums(i) := mem5_stu_prefix_sums(i - 1) + mem5_stu_valid_mask(i - 1).asUInt
    }

    for(i <- 0 until p(STU_NUM)) {
        when(mem5_stu_results(i).valid) {
            mem5_stu_enqs(mem5_stu_prefix_sums(i)) := mem5_stu_results(i)
        }
    }

    for(i <- 0 until bankNum) {
        when(mem5_stu_enqs(i).valid) {
            banks(i)(tailPtr) := mem5_stu_enqs(i)
        }
    }

    
}