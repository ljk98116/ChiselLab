package MyCPU.backend

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

// prepare   enq       select      regread                     ex                                       wb
// prepare   enq                    select        regread(ex + judge ps1/ps2 == pd ?)                   ex
// judge use bypass or prf
class RegReadStage(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val flush = Input(Bool())
        val stall_from_backpipe = Input(Bool())
        // read queue full then stall
        val ldu_items = Input(Vec(p(LDU_NUM), new LDUIQEntry))
        val stu_items = Input(Vec(p(STU_NUM), new STUIQEntry))
        val alu_items = Input(Vec(p(ALU_NUM), new ALUIQEntry))
        val sys_items = Input(Vec(p(SYS_ALU_NUM), new SYSIQEntry))

        // from exu/mem, current calculate res, used for prewakeup uOps
        val aluResult = Input(Vec(p(ALU_NUM), new ALUResult))
        val lduResult = Input(Vec(p(LDU_NUM), new LDUResult))
        val sysResult = Input(Vec(p(SYS_ALU_NUM), new SysResult))

        // to prf
        val ps1ALUReadValid = Output(Vec(p(ALU_NUM), Bool()))
        val ps2ALUReadValid = Output(Vec(p(ALU_NUM), Bool()))
        val ps1ALUReadLoc = Output(Vec(p(ALU_NUM), UInt(p(PREG_WIDTH).W)))
        val ps2ALUReadLoc = Output(Vec(p(ALU_NUM), UInt(p(PREG_WIDTH).W)))
        val ps1ALUReadData = Input(Vec(p(ALU_NUM), UInt(p(DATA_WIDTH).W)))
        val ps2ALUReadData = Input(Vec(p(ALU_NUM), UInt(p(DATA_WIDTH).W)))

        val ps1STUReadValid = Output(Vec(p(STU_NUM), Bool()))
        val ps2STUReadValid = Output(Vec(p(STU_NUM), Bool()))
        val ps1STUReadLoc = Output(Vec(p(STU_NUM), UInt(p(PREG_WIDTH).W)))
        val ps2STUReadLoc = Output(Vec(p(STU_NUM), UInt(p(PREG_WIDTH).W)))
        val ps1STUReadData = Input(Vec(p(STU_NUM), UInt(p(DATA_WIDTH).W)))
        val ps2STUReadData = Input(Vec(p(STU_NUM), UInt(p(DATA_WIDTH).W)))

        val ps1LDUReadValid = Output(Vec(p(LDU_NUM), Bool()))
        val ps1LDUReadLoc = Output(Vec(p(LDU_NUM), UInt(p(PREG_WIDTH).W)))
        val ps1LDUReadData = Input(Vec(p(LDU_NUM), UInt(p(DATA_WIDTH).W)))

        val ps1SYSEXUReadValid = Output(Vec(p(SYS_ALU_NUM), Bool()))
        val ps2SYSEXUReadValid = Output(Vec(p(SYS_ALU_NUM), Bool()))
        val ps1SYSEXUReadLoc = Output(Vec(p(SYS_ALU_NUM), UInt(p(PREG_WIDTH).W)))
        val ps2SYSEXUReadLoc = Output(Vec(p(SYS_ALU_NUM), UInt(p(PREG_WIDTH).W)))
        val ps1SYSEXUReadData = Input(Vec(p(SYS_ALU_NUM), UInt(p(DATA_WIDTH).W)))
        val ps2SYSEXUReadData = Input(Vec(p(SYS_ALU_NUM), UInt(p(DATA_WIDTH).W)))

        val alu_items_ready = Output(Vec(p(ALU_NUM), new ALUInputInfo))
        val ldu_items_ready = Output(Vec(p(LDU_NUM), new LDUInputInfo))
        val stu_items_ready = Output(Vec(p(STU_NUM), new STUInputInfo))
        val sys_items_ready = Output(Vec(p(SYS_ALU_NUM), new SYSEXUInputInfo))  
    })

    val ldu_items = PipeReg(io.ldu_items, io.stall_from_backpipe, io.flush)
    val stu_items = PipeReg(io.stu_items, io.stall_from_backpipe, io.flush)
    val alu_items = PipeReg(io.alu_items, io.stall_from_backpipe, io.flush)
    val sys_items = PipeReg(io.sys_items, io.stall_from_backpipe, io.flush)

    val results = WireInit(VecInit(
        Seq.fill(p(ALU_NUM) + p(LDU_NUM) + p(SYS_ALU_NUM))(0.U.asTypeOf(new PRFWriteReq))
    ))

    for(i <- 0 until p(ALU_NUM)) {
        results(i).HasRd := io.aluResult(i).HasRd
        results(i).pd := io.aluResult(i).pd
        results(i).rd := io.aluResult(i).rd
        results(i).result := io.aluResult(i).result
        results(i).valid := io.aluResult(i).valid
    }

    for(i <- 0 until p(LDU_NUM)) {
        val idx = i + p(ALU_NUM)
        results(idx).HasRd := io.lduResult(i).HasRd
        results(idx).pd := io.lduResult(i).pd
        results(idx).rd := io.lduResult(i).rd
        results(idx).result := io.lduResult(i).result
        results(idx).valid := io.lduResult(i).valid        
    }

    for(i <- 0 until p(SYS_ALU_NUM)) {
        val idx = i + p(ALU_NUM) + p(LDU_NUM)
        results(idx).HasRd := io.sysResult(i).HasRd
        results(idx).pd := io.sysResult(i).pd
        results(idx).rd := io.sysResult(i).rd
        results(idx).result := io.sysResult(i).result
        results(idx).valid := io.sysResult(i).valid        
    }

    for(i <- 0 until p(ALU_NUM)) {
        val ps1HitMask = WireInit(VecInit(
            Seq.fill(p(ALU_NUM) + p(LDU_NUM) + p(SYS_ALU_NUM))(false.B)
        ))
        val ps2HitMask = WireInit(VecInit(
            Seq.fill(p(ALU_NUM) + p(LDU_NUM) + p(SYS_ALU_NUM))(false.B)
        ))

        ps1HitMask := VecInit((0 until p(ALU_NUM) + p(LDU_NUM) + p(SYS_ALU_NUM)).map(
            j => results(j).HasRd && results(j).pd === alu_items(i).ps1 && alu_items(i).HasRs1
        ))

        ps2HitMask := VecInit((0 until p(ALU_NUM) + p(LDU_NUM) + p(SYS_ALU_NUM)).map(
            j => results(j).HasRd && results(j).pd === alu_items(i).ps2 && alu_items(i).HasRs2
        ))

        val hasPs1Hit = ps1HitMask.asUInt.orR
        val hasPs2Hit = ps2HitMask.asUInt.orR

        val ps1HitIndex = PriorityEncoder(ps1HitMask)
        val ps2HitIndex = PriorityEncoder(ps2HitMask)       

        io.alu_items_ready(i).uop := alu_items(i)

        io.alu_items_ready(i).ps1_data := Mux(hasPs1Hit, results(ps1HitIndex).result, io.ps1ALUReadData(i))
        io.alu_items_ready(i).ps2_data := Mux(hasPs2Hit, results(ps2HitIndex).result, io.ps2ALUReadData(i))

        io.ps1ALUReadValid(i) := alu_items(i).HasRs1
        io.ps1ALUReadLoc(i) := alu_items(i).ps1

        io.ps2ALUReadValid(i) := alu_items(i).HasRs2
        io.ps2ALUReadLoc(i) := alu_items(i).ps2
    }
    
    for(i <- 0 until p(STU_NUM)) {
        val ps1HitMask = WireInit(VecInit(
            Seq.fill(p(ALU_NUM) + p(LDU_NUM) + p(SYS_ALU_NUM))(false.B)
        ))
        val ps2HitMask = WireInit(VecInit(
            Seq.fill(p(ALU_NUM) + p(LDU_NUM) + p(SYS_ALU_NUM))(false.B)
        ))

        ps1HitMask := VecInit((0 until p(ALU_NUM) + p(LDU_NUM) + p(SYS_ALU_NUM)).map(
            j => results(j).HasRd && results(j).pd === stu_items(i).ps1 && stu_items(i).HasRs1
        ))

        ps2HitMask := VecInit((0 until p(ALU_NUM) + p(LDU_NUM) + p(SYS_ALU_NUM)).map(
            j => results(j).HasRd && results(j).pd === stu_items(i).ps2 && stu_items(i).HasRs2
        ))

        val hasPs1Hit = ps1HitMask.asUInt.orR
        val hasPs2Hit = ps2HitMask.asUInt.orR

        val ps1HitIndex = PriorityEncoder(ps1HitMask)
        val ps2HitIndex = PriorityEncoder(ps2HitMask)

        io.stu_items_ready(i).uop := stu_items(i)
        io.stu_items_ready(i).ps1_data := Mux(hasPs1Hit, results(ps1HitIndex).result, io.ps1STUReadData(i))
        io.stu_items_ready(i).ps2_data := Mux(hasPs2Hit, results(ps2HitIndex).result, io.ps2STUReadData(i))

        io.ps1STUReadValid(i) := stu_items(i).HasRs1
        io.ps1STUReadLoc(i) := stu_items(i).ps1

        io.ps2STUReadValid(i) := stu_items(i).HasRs2
        io.ps2STUReadLoc(i) := stu_items(i).ps2
    }

    for(i <- 0 until p(LDU_NUM)) {
        val ps1HitMask = WireInit(VecInit(
            Seq.fill(p(ALU_NUM) + p(LDU_NUM) + p(SYS_ALU_NUM))(false.B)
        ))

        ps1HitMask := VecInit((0 until p(ALU_NUM) + p(LDU_NUM) + p(SYS_ALU_NUM)).map(
            j => results(j).HasRd && results(j).pd === ldu_items(i).ps1 && ldu_items(i).HasRs1
        ))

        val hasPs1Hit = ps1HitMask.asUInt.orR
        val ps1HitIndex = PriorityEncoder(ps1HitMask)

        io.ldu_items_ready(i).uop := ldu_items(i)
        io.ldu_items_ready(i).ps1_data := Mux(hasPs1Hit, results(ps1HitIndex).result, io.ps1LDUReadData(i))

        io.ps1LDUReadValid(i) := ldu_items(i).HasRs1
        io.ps1LDUReadLoc(i) := ldu_items(i).ps1      
    }

    for(i <- 0 until p(SYS_ALU_NUM)) {
        val ps1HitMask = WireInit(VecInit(
            Seq.fill(p(ALU_NUM) + p(LDU_NUM) + p(SYS_ALU_NUM))(false.B)
        ))
        val ps2HitMask = WireInit(VecInit(
            Seq.fill(p(ALU_NUM) + p(LDU_NUM) + p(SYS_ALU_NUM))(false.B)
        ))

        ps1HitMask := VecInit((0 until p(ALU_NUM) + p(LDU_NUM) + p(SYS_ALU_NUM)).map(
            j => results(j).HasRd && results(j).pd === sys_items(i).ps1 && sys_items(i).HasRs1
        ))

        ps2HitMask := VecInit((0 until p(ALU_NUM) + p(LDU_NUM) + p(SYS_ALU_NUM)).map(
            j => results(j).HasRd && results(j).pd === sys_items(i).ps2 && sys_items(i).HasRs2
        ))

        val hasPs1Hit = ps1HitMask.asUInt.orR
        val hasPs2Hit = ps2HitMask.asUInt.orR

        val ps1HitIndex = PriorityEncoder(ps1HitMask)
        val ps2HitIndex = PriorityEncoder(ps2HitMask)

        io.sys_items_ready(i).uop := sys_items(i)
        io.sys_items_ready(i).ps1_data := Mux(hasPs1Hit, results(ps1HitIndex).result, io.ps1SYSEXUReadData(i))
        io.sys_items_ready(i).ps2_data := Mux(hasPs2Hit, results(ps2HitIndex).result, io.ps2SYSEXUReadData(i))

        io.ps1SYSEXUReadValid(i) := sys_items(i).HasRs1
        io.ps1SYSEXUReadLoc(i) := sys_items(i).ps1

        io.ps2SYSEXUReadValid(i) := sys_items(i).HasRs2
        io.ps2SYSEXUReadLoc(i) := sys_items(i).ps2        
    }
}