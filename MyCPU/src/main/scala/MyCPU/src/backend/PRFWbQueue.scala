package MyCPU.backend

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

class PRFWbQueue(size: Int, bankNum: Int)(implicit p: Parameters) extends Module {
    val ptrWidth = log2Ceil(size)
    val bankBits = log2Ceil(bankNum)

    val io = IO(new Bundle {
        val aluResult = Input(Vec(p(ALU_NUM), new ALUResult))
        val lduResult = Input(Vec(p(LDU_NUM), new LDUResult))
        val sysResult = Input(Vec(p(SYS_ALU_NUM), new SysResult))
        val enqReady = Output(Bool())
        val deq = Output(Vec(bankNum, new PRFWriteReq))
    })

    val skidBuffers = Seq.fill(bankNum)(
        Module(new MultiPortQueueNW1R(size / bankNum, bankNum, new PRFWriteReq))
    )

    val results = WireInit(VecInit(
        Seq.fill(bankNum)(0.U.asTypeOf(new PRFWriteReq))
    ))

    for(i <- 0 until p(ALU_NUM)) {
        results(i).HasRd := io.aluResult(i).HasRd
        results(i).pd := io.aluResult(i).pd
        results(i).result := io.aluResult(i).result
        results(i).valid := io.aluResult(i).valid
    }

    for(i <- 0 until p(LDU_NUM)) {
        val idx = i + p(ALU_NUM)
        results(idx).HasRd := io.lduResult(i).HasRd
        results(idx).pd := io.lduResult(i).pd
        results(idx).result := io.lduResult(i).result
        results(idx).valid := io.lduResult(i).valid        
    }

    for(i <- 0 until p(SYS_ALU_NUM)) {
        val idx = i + p(ALU_NUM) + p(LDU_NUM)
        results(idx).HasRd := io.sysResult(i).HasRd
        results(idx).pd := io.sysResult(i).pd
        results(idx).result := io.sysResult(i).result
        results(idx).valid := io.sysResult(i).valid        
    }

    for(b <- 0 until bankNum) {
        val bankHits = (0 until bankNum).map(
            index => results(index).pd(bankBits - 1, 0) === b.U && 
                results(index).valid 
        )

        val hasBankHit = VecInit(bankHits).asUInt.orR
        val bankHitIndex = PriorityEncoder(bankHits)

        io.deq(b) := Mux(
            skidBuffers(b).io.deqReady, 
            skidBuffers(b).io.deq, 
            Mux(hasBankHit, results(bankHitIndex), 0.U.asTypeOf(new PRFWriteReq))
        )

        skidBuffers(b).io.deqValid := true.B

        // wb1 calculate the prefix sum, 
        val wb1_bankHitPrefixSum = WireInit(VecInit(
            Seq.fill(bankNum)(0.U(log2Ceil(bankNum).W))
        ))

        wb1_bankHitPrefixSum(0) := 0.U
        for(i <- 1 until bankNum) {
            wb1_bankHitPrefixSum(i) := wb1_bankHitPrefixSum(i-1) + bankHits(i-1) & (
                !hasBankHit || (hasBankHit && (i-1).U =/= bankHitIndex)
            )
        }

        val wb2_bankHitPrefixSum = RegNext(wb1_bankHitPrefixSum)
        val wb2_enq = RegNext(results)

        for(i <- 0 until bankNum) {
            skidBuffers(b).io.enq(i) := wb2_enq(wb2_bankHitPrefixSum(i))
            skidBuffers(b).io.enqValid(i) := wb2_enq(wb2_bankHitPrefixSum(i)).valid
        }

        skidBuffers(b).io.flush := false.B
    }

    io.enqReady := Cat((0 until bankNum).map(
        b => skidBuffers(b).io.enqReady
    )).andR
}