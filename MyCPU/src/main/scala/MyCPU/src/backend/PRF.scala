package MyCPU.backend

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

// 1 read, bankNum write
// assume banked write input
class PRFTable(bankNum: Int, size: Int)(implicit p: Parameters) extends Module {
    val ptrWidth = log2Ceil(size) // 8bit
    val perBankSize = size / bankNum // 132 / 6 = 22
    val perBankPtrWidth = log2Ceil(perBankSize) // 5
    val bankBits = log2Ceil(bankNum) // 3

    val io = IO(new Bundle {
        val ren = Input(Bool())
        val raddr = Input(UInt(ptrWidth.W))
        val rdata = Output(UInt(p(DATA_WIDTH).W))
        val wen = Input(Vec(bankNum, Bool()))
        val waddr = Input(Vec(bankNum, UInt(ptrWidth.W)))
        val wdata = Input(Vec(bankNum, UInt(p(DATA_WIDTH).W)))
    })

    val bankMems = Seq.fill(bankNum)(Mem(perBankSize, UInt(p(DATA_WIDTH).W)))

    val readBank = io.raddr(bankBits - 1, 0)
    val readRow = io.raddr(ptrWidth - 1, bankBits)

    io.rdata := 0.U

    for(b <- 0 until bankNum) {
        when((b.U === readBank) && io.ren) {
            io.rdata := bankMems(b).read(readRow)
        }
        when(io.wen(b)) {
            bankMems(b).write(io.waddr(b)(ptrWidth - 1, bankBits), io.wdata(b))
        }
    }
}

// readPorts RegFile Table copy to read all reqs at same cycle
// bankNum writePorts, write all tables
class PRF(implicit p: Parameters) extends Module {
    val realPregNum = 1 << p(PREG_WIDTH)
    val readPorts = (p(ALU_NUM) + p(STU_NUM) + p(SYS_ALU_NUM)) * 2 + p(LDU_NUM) // 13
    val bankNum = p(ALU_NUM) + p(LDU_NUM) + p(SYS_ALU_NUM) // 6

    var prfTableSize = realPregNum / bankNum * bankNum 
    if(realPregNum % bankNum != 0) {
        prfTableSize = (realPregNum / bankNum + 1) * bankNum
    }

    val prfTablePtrWidth = log2Ceil(prfTableSize)

    // 132 x 32bits x 13 = 132x4B = 528B x 13 = 6.5KB total
    val io = IO(new Bundle {
        val flush = Input(Bool())

        val ps1ALUReadValid = Input(Vec(p(ALU_NUM), Bool()))
        val ps2ALUReadValid = Input(Vec(p(ALU_NUM), Bool()))
        val ps1ALUReadLoc = Input(Vec(p(ALU_NUM), UInt(p(PREG_WIDTH).W)))
        val ps2ALUReadLoc = Input(Vec(p(ALU_NUM), UInt(p(PREG_WIDTH).W)))
        val ps1ALUReadData = Output(Vec(p(ALU_NUM), UInt(p(DATA_WIDTH).W)))
        val ps2ALUReadData = Output(Vec(p(ALU_NUM), UInt(p(DATA_WIDTH).W)))

        val ps1STUReadValid = Input(Vec(p(STU_NUM), Bool()))
        val ps2STUReadValid = Input(Vec(p(STU_NUM), Bool()))
        val ps1STUReadLoc = Input(Vec(p(STU_NUM), UInt(p(PREG_WIDTH).W)))
        val ps2STUReadLoc = Input(Vec(p(STU_NUM), UInt(p(PREG_WIDTH).W)))
        val ps1STUReadData = Output(Vec(p(STU_NUM), UInt(p(DATA_WIDTH).W)))
        val ps2STUReadData = Output(Vec(p(STU_NUM), UInt(p(DATA_WIDTH).W)))

        val ps1LDUReadValid = Input(Vec(p(LDU_NUM), Bool()))
        val ps1LDUReadLoc = Input(Vec(p(LDU_NUM), UInt(p(PREG_WIDTH).W)))
        val ps1LDUReadData = Output(Vec(p(LDU_NUM), UInt(p(DATA_WIDTH).W)))

        val ps1SYSEXUReadValid = Input(Vec(p(SYS_ALU_NUM), Bool()))
        val ps2SYSEXUReadValid = Input(Vec(p(SYS_ALU_NUM), Bool()))
        val ps1SYSEXUReadLoc = Input(Vec(p(SYS_ALU_NUM), UInt(p(PREG_WIDTH).W)))
        val ps2SYSEXUReadLoc = Input(Vec(p(SYS_ALU_NUM), UInt(p(PREG_WIDTH).W)))
        val ps1SYSEXUReadData = Output(Vec(p(SYS_ALU_NUM), UInt(p(DATA_WIDTH).W)))
        val ps2SYSEXUReadData = Output(Vec(p(SYS_ALU_NUM), UInt(p(DATA_WIDTH).W)))

        val aluResult = Input(Vec(p(ALU_NUM), new ALUResult))
        val lduResult = Input(Vec(p(LDU_NUM), new LDUResult))
        val sysResult = Input(Vec(p(SYS_ALU_NUM), new SysResult))

        val freeRegIdBufferResp = Input(Vec(p(FETCH_WIDTH), new FreeRegIdBufferReadResp))
        // may account for busytable checkpoint
        val busyTableCheckPoints = Output(Vec(p(FETCH_WIDTH), UInt(realPregNum.W)))
        val busyTableCheckPoint = Input(UInt(realPregNum.W)) 
        // busy table io
        val busyTable = Output(UInt((realPregNum).W))
        // prf wb queue full
        val wb1_stall = Output(Bool())
    })

    val pregs = Seq.fill(readPorts)(
        Module(new PRFTable(bankNum, prfTableSize))
    )

    val prfWbQueue = Module(new PRFWbQueue(bankNum * bankNum * bankNum, bankNum))

    prfWbQueue.io.aluResult := io.aluResult
    prfWbQueue.io.lduResult := io.lduResult
    prfWbQueue.io.sysResult := io.sysResult

    io.wb1_stall := !prfWbQueue.io.enqReady

    for(i <- 0 until p(ALU_NUM)) {
        val idx1 = i * 2
        val idx2 = i * 2 + 1

        io.ps1ALUReadData(i) := pregs(idx1).io.rdata
        pregs(idx1).io.ren := io.ps1ALUReadValid(i)
        pregs(idx1).io.raddr := Cat(Fill(prfTablePtrWidth - p(PREG_WIDTH), false.B), io.ps1ALUReadLoc(i))
    
        io.ps2ALUReadData(i) := pregs(idx2).io.rdata
        pregs(idx2).io.ren := io.ps2ALUReadValid(i)
        pregs(idx2).io.raddr := Cat(Fill(prfTablePtrWidth - p(PREG_WIDTH), false.B), io.ps2ALUReadLoc(i))        
    }

    for(i <- 0 until p(STU_NUM)) {
        val idx1 = i * 2 + p(ALU_NUM) * 2
        val idx2 = i * 2 + 1 + p(ALU_NUM) * 2

        io.ps1STUReadData(i) := pregs(idx1).io.rdata
        pregs(idx1).io.ren := io.ps1STUReadValid(i)
        pregs(idx1).io.raddr := Cat(Fill(prfTablePtrWidth - p(PREG_WIDTH), false.B), io.ps1STUReadLoc(i))
    
        io.ps2STUReadData(i) := pregs(idx2).io.rdata
        pregs(idx2).io.ren := io.ps2STUReadValid(i)
        pregs(idx2).io.raddr := Cat(Fill(prfTablePtrWidth - p(PREG_WIDTH), false.B), io.ps2STUReadLoc(i))         
    }

    for(i <- 0 until p(LDU_NUM)) {
        val idx1 = i + p(ALU_NUM) * 2 + p(STU_NUM) * 2

        io.ps1LDUReadData(i) := pregs(idx1).io.rdata
        pregs(idx1).io.ren := io.ps1LDUReadValid(i)
        pregs(idx1).io.raddr := Cat(Fill(prfTablePtrWidth - p(PREG_WIDTH), false.B), io.ps1LDUReadLoc(i))   
    }

    // sys remain to do
    for(i <- 0 until p(SYS_ALU_NUM)) {
        val idx1 = i * 2 + p(ALU_NUM) * 2 + p(STU_NUM) * 2 + p(LDU_NUM)
        val idx2 = i * 2 + 1 + p(ALU_NUM) * 2 + p(STU_NUM) * 2 + p(LDU_NUM)

        io.ps1SYSEXUReadData(i) := pregs(idx1).io.rdata
        pregs(idx1).io.ren := io.ps1SYSEXUReadValid(i)
        pregs(idx1).io.raddr := Cat(Fill(prfTablePtrWidth - p(PREG_WIDTH), false.B), io.ps1SYSEXUReadLoc(i))
    
        io.ps2SYSEXUReadData(i) := pregs(idx2).io.rdata
        pregs(idx2).io.ren := io.ps2SYSEXUReadValid(i)
        pregs(idx2).io.raddr := Cat(Fill(prfTablePtrWidth - p(PREG_WIDTH), false.B), io.ps2SYSEXUReadLoc(i))        
    }

    // write pregs
    for(b <- 0 until bankNum) {
        for(i <- 0 until readPorts) {
            pregs(i).io.wen(b) := prfWbQueue.io.deq(b).HasRd
            pregs(i).io.waddr(b) := Cat(Fill(prfTablePtrWidth - p(PREG_WIDTH), false.B), prfWbQueue.io.deq(b).pd)
            pregs(i).io.wdata(b) := prfWbQueue.io.deq(b).result
        }
    }

    val busyTable = RegInit(0.U(realPregNum.W))
    val busyTableAfterRenameVec = Seq.fill(p(FETCH_WIDTH))(
        WireInit(0.U(realPregNum.W))
    )

    val busyTableAfterWb = Seq.fill(bankNum)(
        WireInit(0.U(realPregNum.W))
    )

    for(i <- 0 until p(FETCH_WIDTH)) {
        busyTableAfterRenameVec(i) := busyTable | (io.freeRegIdBufferResp(i).valid << io.freeRegIdBufferResp(i).preg)
        io.busyTableCheckPoints(i) := busyTableAfterRenameVec(i)
    }

    for(i <- 0 until bankNum) {
        busyTableAfterWb(i) := busyTable ^ (prfWbQueue.io.deq(i).HasRd << prfWbQueue.io.deq(i).pd) 
    }

    busyTable := Mux(
        io.flush, io.busyTableCheckPoint, 
        busyTableAfterRenameVec.reduce(_|_) | busyTableAfterWb.reduce(_|_)
    )

    io.busyTable := busyTable
}