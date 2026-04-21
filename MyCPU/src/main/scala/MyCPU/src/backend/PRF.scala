package MyCPU.backend

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

class SingleReg(ports: Int)(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val rens = Input(Vec(ports, Bool()))
        val rdatas = Output(Vec(ports, UInt(p(DATA_WIDTH).W)))
        val wen = Input(Bool())
        val wdata = Input(UInt(p(DATA_WIDTH).W))
    })

    val rVec = RegInit(VecInit(
        Seq.fill(ports)(0.U(p(DATA_WIDTH).W))
    ))

    for(i <- 0 until ports) {
        io.rdatas(i) := Mux(io.rens(i), rVec(i), 0.U)
        rVec(i) := Mux(io.wen, io.wdata, rVec(i))
    }
}

// (alu_num + ldu_num + sys_exu_num) banks for write
// (alu_num x 2 + ldu_num + sys_exu_num x 2 + stu_num x 2) ports ports for read, is reg copy times
class PRF(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
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
        val ps1STUReadData = Output(Vec(p(ALU_NUM), UInt(p(DATA_WIDTH).W)))
        val ps2STUReadData = Output(Vec(p(ALU_NUM), UInt(p(DATA_WIDTH).W)))

        val ps1LDUReadValid = Input(Vec(p(LDU_NUM), Bool()))
        val ps1LDUReadLoc = Input(Vec(p(LDU_NUM), UInt(p(PREG_WIDTH).W)))
        val ps1LDUReadData = Output(Vec(p(ALU_NUM), UInt(p(DATA_WIDTH).W)))

        val aluResult = Input(Vec(p(ALU_NUM), new ALUResult))
        val lduResult = Input(Vec(p(LDU_NUM), new LDUResult))
    })

    val readPorts = p(ALU_NUM) * 2 + p(STU_NUM) * 2 + p(LDU_NUM) + p(SYS_ALU_NUM) // 3 x 2 + 2 x 2 + 2 + 1 = 13
    val bankNum = p(ALU_NUM) + p(LDU_NUM) + p(SYS_ALU_NUM) // 3 + 2 + 1 = 6

    val realPregNum = 1 << p(PREG_WIDTH) // 128 
    var usedPregNum = realPregNum
    if(realPregNum % bankNum == 0) {
        usedPregNum = (realPregNum / bankNum + 1) * bankNum
    }
    val perBankSize = usedPregNum / bankNum

    // write for all banks
    // read may in a single bank
    val pregs = Seq.fill(usedPregNum)(Module(new SingleReg(readPorts)))
    
    for(i <- 0 until usedPregNum) {
        for(b <- 0 until readPorts) {
            pregs(i).io.rens(b) := false.B
        }
        pregs(i).io.wen := false.B
        pregs(i).io.wdata := 0.U
    }

    for(i <- 0 until p(ALU_NUM)) {
        val idx1 = 2 * i
        val idx2 = 2 * i + 1
        io.ps1ALUReadData(i) := 0.U
        io.ps2ALUReadData(i) := 0.U
        for(j <- 0 until realPregNum) {
            when(io.ps1ALUReadValid(i) && io.ps1ALUReadLoc(i) === j.U) {
                pregs(j).io.rens(idx1) := true.B
                io.ps1ALUReadData(i) := pregs(j).io.rdatas(idx1)
            }
            when(io.ps2ALUReadValid(i) && io.ps2ALUReadLoc(i) === j.U) {
                pregs(j).io.rens(idx2) := true.B
                io.ps2ALUReadData(i) := pregs(j).io.rdatas(idx2)
            }
        }
    }

    for(i <- 0 until p(LDU_NUM)) {
        val idx1 = i + p(ALU_NUM) * 2
        io.ps1LDUReadData(i) := 0.U
        for(j <- 0 until realPregNum) {
            when(io.ps1LDUReadValid(i) && io.ps1LDUReadLoc(i) === j.U) {
                pregs(j).io.rens(idx1) := true.B
                io.ps1LDUReadData(i) := pregs(j).io.rdatas(idx1)
            }
        }        
    }

    for(i <- 0 until p(STU_NUM)) {
        val idx1 = 2 * i + p(LDU_NUM) + p(ALU_NUM) * 2
        val idx2 = 2 * i + 1 + p(LDU_NUM) + p(ALU_NUM) * 2
        io.ps1STUReadData(i) := 0.U
        io.ps2STUReadData(i) := 0.U
        for(j <- 0 until realPregNum) {
            when(io.ps1STUReadValid(i) && io.ps1STUReadLoc(i) === j.U) {
                pregs(j).io.rens(idx1) := true.B
                io.ps1STUReadData(i) := pregs(j).io.rdatas(idx1)
            }
            when(io.ps2STUReadValid(i) && io.ps2STUReadLoc(i) === j.U) {
                pregs(j).io.rens(idx2) := true.B
                io.ps2STUReadData(i) := pregs(j).io.rdatas(idx2)
            }
        }        
    }

    
}