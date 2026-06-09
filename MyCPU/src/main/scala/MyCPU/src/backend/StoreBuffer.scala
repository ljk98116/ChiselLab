package MyCPU.backend

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

// load check pipeline
// stage0 read all datas and check equal[load stage0]
// stage1 send check result [load stage1]
// stage0 store enq
// when dcache write ready, deq, drop head
class StoreBuffer(size: Int)(implicit p: Parameters) extends Module {
    val bankNum = p(STU_NUM)
    val bankBits = log2Ceil(bankNum)
    val ptrWidth = log2Ceil(size)
    val perBankSize = size / bankNum

    val io = IO(new Bundle {
        val flush = Input(Bool())
        val mem0_stu_results = Input(Vec(p(STU_NUM), new AGUResult))
        val mem1_st_stalls = Input(p(STU_NUM), Bool())
        val mem1_ldu_results = Input(Vec(p(LDU_NUM), new AGUResult))
        val storeBufferDeqEnable = Input(Bool())
        val mem0_storeBufferCanEnq = Output(Bool())
        val mem1_storeBuffer_resps = Output(Vec(p(LDU_NUM), new StoreBufferRespInfo))
    })

    val banks = Seq.fill(bankNum)(
        RegInit(VecInit(
            Seq.fill(perBankSize)(0.U.asTypeOf(new AGUResult))
        ))
    )

    val headPtr = RegInit(0.U(ptrWidth.W))
    val tailPtr = RegInit(0.U(ptrWidth.W))

    val mem0_stu_mask = VecInit(
        io.mem0_stu_results.map(_.valid)
    )

    val mem0_stu_prefix_sums = WireInit(VecInit(
        Seq.fill(p(STU_NUM))(0.U(bankBits.W))
    ))

    val mem1_stu_results = VecInit((0 until p(STU_NUM)).map(i =>
        PipeReg(io.mem0_stu_results(i), io.mem1_st_stalls(i), io.flush)    
    ))

    val mem1_stu_prefix_sums = VecInit((0 until p(STU_NUM)).map(i => 
        PipeReg(mem0_stu_prefix_sums(i), io.mem1_st_stalls(i), io.flush)    
    ))
    
    mem0_stu_prefix_sums(0) := 0.U
    for(i <- 1 until p(STU_NUM)) {
        mem0_stu_prefix_sums(i) := mem0_stu_prefix_sums(i-1) + mem0_stu_mask(i-1)
    }


}