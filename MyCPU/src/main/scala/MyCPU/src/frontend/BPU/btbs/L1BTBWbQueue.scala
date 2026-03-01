package MyCPU.frontend.BPU

import chisel3._
import chisel3.util._

import MyCPU.config._
import MyCPU.io._
import org.chipsalliance.cde.config._

// Multi Write/ Multi Read queue for L1 BTB update
// multi bank write to regfile, combine read
// write at commit stage, read fetch width's btb update info to update btb
class L1BTBWbQueue(size: Int)(implicit p: Parameters) extends Module {
    val io = IO(new Bundle{
        val enq = Input(Vec(p(FETCH_WIDTH), new L1BTBUpdateInfo))
        val deq = Output(Vec(p(FETCH_WIDTH), new L1BTBUpdateInfo))
        val wr_able = Output(Bool())
    })

    require(p(L1BTBParam).btbBankNum == p(FETCH_WIDTH), "l1 btb wbqueue bankNum must equal to fetch width")
    require(size % p(FETCH_WIDTH) == 0, "invalid l1 btb wbqueue size")
    val offsetBits = p(L1BTBParam).btbOffsetBits
    val ptrWidth = log2Ceil(size / p(FETCH_WIDTH))
    val preBankSize = size / p(FETCH_WIDTH)
    val bankNum = p(L1BTBParam).btbBankNum
    val _1stBankBits = log2Ceil(bankNum)
    val _2ndBankBits = log2Ceil(p(FETCH_WIDTH))

    require(_1stBankBits + offsetBits + _2ndBankBits < p(ADDR_WIDTH), "l1 btb wbqueue bank id overflow")

    // Queue banks: smaller per-bank storage size
    val queue_banks = RegInit(VecInit(Seq.fill(p(FETCH_WIDTH))(
        VecInit(Seq.fill(bankNum)(VecInit(Seq.fill(preBankSize)(0.U.asTypeOf(new L1BTBUpdateInfo))))))))

    // Read and write pointers
    val read_ptrs = RegInit(VecInit(Seq.fill(p(FETCH_WIDTH))(0.U(ptrWidth.W))))
    val write_ptrs = RegInit(VecInit(Seq.fill(p(FETCH_WIDTH))(0.U(ptrWidth.W))))

    // Data to be read from banks
    val readData = WireInit(VecInit(Seq.fill(p(FETCH_WIDTH))(0.U.asTypeOf(new L1BTBUpdateInfo))))

    // Writing Logic
    for(i <- 0 until p(FETCH_WIDTH)) {
        val prefix_sum = WireInit(VecInit(Seq.fill(p(FETCH_WIDTH))(0.U(ptrWidth.W))))
        prefix_sum(0) := 0.U

        // Calculate prefix sum for the 1st bank to determine which entries to write
        for(j <- 1 until p(FETCH_WIDTH)) {
            val _1stbankid = io.enq(j - 1).pc(_1stBankBits + offsetBits - 1, offsetBits)
            prefix_sum(j) := prefix_sum(j-1) + (io.enq(j - 1).valid && _1stbankid === i.U)
        }

        // For each write, write to the corresponding bank
        for(j <- 0 until p(FETCH_WIDTH)) {
            val global_index = write_ptrs(i) + prefix_sum(j)
            val _2ndbankid = global_index(log2Ceil(p(FETCH_WIDTH)) - 1, 0)
            val _2ndrow = global_index(ptrWidth - 1, log2Ceil(p(FETCH_WIDTH)))

            for(k <- 0 until p(FETCH_WIDTH)) {
                val _1stbankid = io.enq(k).pc(_1stBankBits + offsetBits - 1, offsetBits)
                when(io.enq(k).valid && _1stbankid === i.U) {
                    queue_banks(i)(_2ndbankid)(_2ndrow) := io.enq(k)
                }
            }
        }

        // Per bank write count
        val WriteVec = io.enq.map(enq => enq.valid && enq.pc(_1stBankBits + offsetBits - 1, offsetBits) === i.U)
        val WriteCount = PopCount(WriteVec)

        write_ptrs(i) := write_ptrs(i) + WriteCount

        // Read data from the banks
        val readBank = read_ptrs(i)(log2Ceil(p(FETCH_WIDTH)) - 1, 0)
        val readRow = read_ptrs(i)(ptrWidth - 1, log2Ceil(p(FETCH_WIDTH)))

        readData(i) := queue_banks(i)(readBank)(readRow)
        read_ptrs(i) := read_ptrs(i) + readData(i).valid
    }

    // Output read data and ready signal
    io.deq := readData
    io.wr_able := ~(write_ptrs.map(ptr => ptr === (preBankSize / bankNum).U)).reduce(_ || _)
}