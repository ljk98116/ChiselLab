package MyCPU.backend

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

// calculate sum and carry
class CSA(width: Int)(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val d1 = Input(UInt(width.W))
        val d2 = Input(UInt(width.W))
        val d3 = Input(UInt(width.W))
        val sum = Output(UInt(width.W))
        val carry = Output(UInt(width.W))
    })

    for(i <- 0 until width) {
        io.sum(i) := Cat(io.d1(i), io.d2(i), io.d3(i)).xorR
        io.carry(i) := 
            (io.d1(i) && io.d2(i)) ||
            (io.d1(i) && io.d3(i)) ||
            (io.d2(i) && io.d3(i)) 
    }
}

// Wallace Tree Impl, no pipeline stall, but need several cycles to get result
// 32 / 3 = 10
// 3 cycles CSA 1 cycle add
class MultUnit(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val flush = Input(Bool())
        val stall_from_backpipe = Input(Bool())

        val valid = Input(Bool())
        val funct3 = Input(UInt(3.W)) 
        val data1 = Input(UInt(p(DATA_WIDTH).W))
        val data2 = Input(UInt(p(DATA_WIDTH).W))
        val result = Output(UInt(p(DATA_WIDTH).W)) 

        val ex_stall = Output(Bool())      
    })

    var dataNum = p(DATA_WIDTH)
    // pre compute csa number per cycle and totalCycles
    var csaPerCycle = Array.fill(p(DATA_WIDTH))(0)
    var dataNumPerCycle = Array.fill(p(DATA_WIDTH))(0)
    var totalCycles = 0

    dataNumPerCycle(0) = p(DATA_WIDTH)
    // last cycle used for direct add
    do {
        csaPerCycle(totalCycles) = dataNumPerCycle(totalCycles) / 3
        if(totalCycles + 1 < p(DATA_WIDTH)) {
            dataNumPerCycle(totalCycles + 1) = 
                dataNumPerCycle(totalCycles) - csaPerCycle(totalCycles)
        }
        totalCycles += 1
    } while(dataNum >= 3 && totalCycles < p(DATA_WIDTH))


}