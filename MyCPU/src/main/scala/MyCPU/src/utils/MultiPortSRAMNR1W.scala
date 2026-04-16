package MyCPU.utils

import chisel3._
import chisel3.util._

import MyCPU.config._
import MyCPU.io._
import MyCPU.frontend.BPU._
import MyCPU.frontend.BPU.predictors._
import org.chipsalliance.cde.config._

class MultiPortSRAMNR1W(addrWidth: Int, dataWidth: Int, numReadPorts: Int)(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val readAddrs = Input(Vec(numReadPorts, UInt(addrWidth.W)))
        val readDatas = Output(Vec(numReadPorts, UInt(dataWidth.W)))
        val writeAddr = Input(UInt(addrWidth.W))
        val writeData = Input(UInt(dataWidth.W))
        val writeEnable = Input(Bool())
    })

    val bankBits = log2Ceil(numReadPorts)
    val banks = Seq.fill(numReadPorts)(SyncReadMem(1 << (addrWidth - bankBits), UInt(dataWidth.W)))

    for(b <- 0 until numReadPorts) {
        val bankHits = VecInit(io.readAddrs.map(addr => 
            addr(bankBits - 1, 0) === b.U
        ))

        val hasBankHit = bankHits.asUInt.orR
        val BankHitIndex = PriorityEncoder(bankHits)

        val readRow = io.readAddrs(BankHitIndex)(addrWidth - 1, bankBits)
        val readData = banks(b).read(readRow)

        io.readDatas(b) := Mux(hasBankHit, readData, 0.U)
    }

    val writeBank = io.writeAddr(bankBits - 1, 0)
    val writeRow = io.writeAddr(addrWidth - 1, bankBits)
    for(b <- 0 until numReadPorts) {
        when(writeBank === b.U && io.writeEnable) {
            banks(b).write(writeRow, io.writeData)
        }
    }
}