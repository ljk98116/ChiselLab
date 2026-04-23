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

    // totalCycles - 1 cycles have csa adder
    // totalCycles - 1 cycles have csa result reg
    val csaAddData = Seq.tabulate(totalCycles - 1)(
        c =>
            Seq.fill(dataNumPerCycle(c + 1))(RegInit(0.U((p(DATA_WIDTH) + 2).W)))
    )

    val csaAddDataShift = Seq.tabulate(totalCycles - 1)(
        c => Seq.fill(dataNumPerCycle(c + 1))(RegInit(0.U(log2Ceil(p(DATA_WIDTH)).W)))
    )
    
    val csaModules = Seq.tabulate(totalCycles - 1)(
        c => Seq.fill(dataNumPerCycle(c + 1))(Module(new CSA(p(DATA_WIDTH) + 2)))
    )

    val dataPlain = WireInit(VecInit(
        Seq.fill(p(DATA_WIDTH))(0.U(p(DATA_WIDTH).W))
    ))

    for(c <- 0 until p(DATA_WIDTH)) {
        dataPlain(c) := Mux(io.data2(c), io.data1, 0.U) 
    }

    val result = WireInit(0.U((p(DATA_WIDTH) * 2).W))

    // mult pipeline
    for(c <- 0 until totalCycles - 1) {

        val csaAddDataStart = WireInit(VecInit(
            Seq.fill(dataNumPerCycle(c))(0.U((p(DATA_WIDTH) + 2).W))
        ))

        for(d <- 0 until dataNumPerCycle(c)) {
            if(c == 0) {
                csaAddDataStart(d) := dataPlain(d)
            }
            else {
                csaAddDataStart(d) := csaAddData(c-1)(d)
            }
        }

        for(d <- 0 until csaPerCycle(c)) {
            csaModules(c)(d).io.d1 := csaAddDataStart(3 * d)
            csaModules(c)(d).io.d2 := csaAddDataStart(3 * d + 1) << 1.U
            csaModules(c)(d).io.d3 := csaAddDataStart(3 * d + 2) << 2.U
            
            if(c == 0) {
                csaAddDataShift(c)(2 * d) := (3 * d).U
                csaAddDataShift(c)(2 * d + 1) := (3 * d + 1).U
            }
            else {
                csaAddDataShift(c)(2 * d) := csaAddDataShift(c-1)(3 * d)
                csaAddDataStart(c)(2 * d + 1) := csaAddDataShift(c-1)(3 * d + 1)
            }

            csaAddData(c)(2 * d) := csaModules(c)(d).io.sum
            csaAddData(c)(2 * d + 1) := csaModules(c)(d).io.carry
        }

        val dataLeftNum = dataNumPerCycle(c) - csaPerCycle(c) * 3
        for(d <- 0 until dataLeftNum) {
            csaAddData(c)(2 * csaPerCycle(c) + d) := csaAddDataStart(3 * csaPerCycle(c) + d)
            if(c == 0) {
                csaAddDataShift(c)(2 * csaPerCycle(c) + d) := (3 * csaPerCycle(c) + d).U
            }
            else {
                csaAddDataShift(c)(2 * csaPerCycle(c) + d) := csaAddDataShift(c-1)(3 * csaPerCycle(c) + d)
            }
        }
    }

    val addData1 = WireInit(0.U((p(DATA_WIDTH) * 2).W))
    val addData2 = WireInit(0.U((p(DATA_WIDTH) * 2).W))

    addData1 := csaAddData(totalCycles - 1)(0)
    addData2 := csaAddData(totalCycles - 1)(1)

    io.result := 
        (addData1 << csaAddDataShift(totalCycles - 1)(0)) + 
        (addData2 << csaAddDataShift(totalCycles - 1)(1))
}