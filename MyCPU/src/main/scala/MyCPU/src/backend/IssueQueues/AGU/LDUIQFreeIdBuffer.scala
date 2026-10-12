package MyCPU.backend

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

class LDUIQFreeIdBuffer(implicit p: Parameters) extends Module {
    val totalSize = p(LDUIQ_SIZE)
    val ptrWidth = log2Ceil(totalSize)
    val perBankSize = totalSize / p(LDU_NUM)
    val perBankPtrWith = log2Ceil(perBankSize)
    val bankBits = log2Ceil(p(LDU_NUM))

    val io = IO(new Bundle {
        val flush = Input(Bool())
        val stalls = Input(Vec(p(LDU_NUM), Bool()))
        val allocReq = Input(Vec(p(FETCH_WIDTH), Bool()))
        val allocResp = Output(Vec(p(FETCH_WIDTH), UInt(ptrWidth.W)))
        // select stage
        val writeReq = Input(Vec(p(LDU_NUM), new LDUIQFreeIdBufferWriteReq))
        val deqReady = Output(Bool())
    })

    val headPtr = RegInit(0.U(ptrWidth.W))
    val tailPtr = RegInit((totalSize - 1).U(ptrWidth.W))

    val buffer = RegInit(VecInit(
        Seq.tabulate(p(LDU_NUM))(
            b => VecInit(Seq.tabulate(perBankSize)(
                i => (b * perBankSize + i).U(ptrWidth.W)
            ))
        )
    ))

    val readCount = PopCount(io.allocReq)

    val recycle_writeReq = VecInit((0 until p(LDU_NUM)).map(i => 
        PipeReg(io.writeReq(i), io.stalls(i), io.flush)    
    ))
    // when stall happened, bank can not write
    val recycle_writeReqValidMask = VecInit(recycle_writeReq.map(_.valid)).asUInt & io.stalls.asUInt
    val writeCount = PopCount(recycle_writeReqValidMask.asBools)

    val used = tailPtr - headPtr
    val free = totalSize.U - used

    io.deqReady := free + readCount <= totalSize.U

    // read
    val readData = WireInit(VecInit(
        Seq.fill(p(FETCH_WIDTH))(0.U(ptrWidth.W))
    ))

    for(i <- 0 until p(FETCH_WIDTH)) {
        val rd_idx = headPtr + i.U
        val rd_bank = rd_idx(bankBits - 1, 0)
        val rd_row = rd_idx(ptrWidth - 1, bankBits)

        for(b <- 0 until p(LDU_NUM)) {
            when(b.U === rd_bank && io.allocReq(i) && io.deqReady) {
                readData(i) := buffer(b)(rd_row)
            }
        }

        io.allocResp(i) := readData(i)
    }

    // write
    // calculate prefix sum
    val writePrefixSum = WireInit(VecInit(
        Seq.fill(p(LDU_NUM))(0.U(bankBits.W))
    ))

    writePrefixSum(0) := 0.U
    for(i <- 1 until p(LDU_NUM)) {
        writePrefixSum(i) := writePrefixSum(i-1) + recycle_writeReqValidMask.asBools(i-1)
    }

    for(i <- 0 until p(LDU_NUM)) {
        val writeIdx = tailPtr + writePrefixSum(i)
        val writeBank = writeIdx(bankBits - 1, 0)
        val writeRow = writeIdx(ptrWidth - 1, bankBits)

        for(b <- 0 until p(LDU_NUM)) {
            when((b.U === writeBank) && io.writeReq(i).valid && !io.stalls(b)) {
                buffer(b)(writeRow) := io.writeReq(i).id
            }
        }
    }

    headPtr := Mux(io.flush, 0.U, headPtr + readCount)
    tailPtr := Mux(io.flush, (totalSize - 1).U, tailPtr + writeCount)    
}