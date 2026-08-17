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
// dispatch alu insts to their subunits
class RegReadStage(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val flush = Input(Bool())
        val stall_from_backpipe = Input(Bool())
        // read queue full then stall
        val ldu_items = Input(Vec(p(LDU_NUM), new LDUIQEntry))
        val stu_items = Input(Vec(p(STU_NUM), new STUIQEntry))
        val alu_items = Input(Vec(p(ALU_NUM), new ALUIQEntry))
        val sys_items = Input(Vec(p(SYS_ALU_NUM), new SYSIQEntry))
        val mult_items = Input(Vec(p(MULT_NUM), new ALUIQEntry))
        val div_items = Input(Vec(p(DIV_NUM), new ALUIQEntry))

        // from exu/mem, current calculate res, used for prewakeup uOps
        val aluResult = Input(Vec(p(ALU_NUM), new ALUResult))
        val lduResult = Input(Vec(p(LDU_NUM), new LDUResult))
        val sysResult = Input(Vec(p(SYS_ALU_NUM), new SysResult))
        val multResult = Input(Vec(p(MULT_NUM), new ALUResult))
        val divResult = Input(Vec(p(DIV_NUM), new ALUResult))

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

        val ps1MULTReadValid = Output(Vec(p(MULT_NUM), Bool()))
        val ps2MULTReadValid = Output(Vec(p(MULT_NUM), Bool()))
        val ps1MULTReadLoc = Output(Vec(p(MULT_NUM), UInt(p(PREG_WIDTH).W)))
        val ps2MULTReadLoc = Output(Vec(p(MULT_NUM), UInt(p(PREG_WIDTH).W)))
        val ps1MULTReadData = Input(Vec(p(MULT_NUM), UInt(p(DATA_WIDTH).W)))
        val ps2MULTReadData = Input(Vec(p(MULT_NUM), UInt(p(DATA_WIDTH).W)))

        val ps1DIVReadValid = Output(Vec(p(DIV_NUM), Bool()))
        val ps2DIVReadValid = Output(Vec(p(DIV_NUM), Bool()))
        val ps1DIVReadLoc = Output(Vec(p(DIV_NUM), UInt(p(PREG_WIDTH).W)))
        val ps2DIVReadLoc = Output(Vec(p(DIV_NUM), UInt(p(PREG_WIDTH).W)))
        val ps1DIVReadData = Input(Vec(p(DIV_NUM), UInt(p(DATA_WIDTH).W)))
        val ps2DIVReadData = Input(Vec(p(DIV_NUM), UInt(p(DATA_WIDTH).W)))

        // to alu sub units
        val integer_inputs = Output(Vec(p(ALU_NUM), new IntegerUnitInputInfo))
        val branch_inputs = Output(Vec(p(ALU_NUM), new BranchUnitInputInfo))
        val mult_inputs = Output(Vec(p(ALU_NUM), new MultUnitInputInfo))
        val div_inputs = Output(Vec(p(ALU_NUM), new DivUnitInputInfo))

        val alu_items_ready = Output(Vec(p(ALU_NUM), new ALUInputInfo))
        val ldu_items_ready = Output(Vec(p(LDU_NUM), new LDUInputInfo))
        val stu_items_ready = Output(Vec(p(STU_NUM), new STUInputInfo))
        val sys_items_ready = Output(Vec(p(SYS_ALU_NUM), new SYSEXUInputInfo))
        val mult_items_ready = Output(Vec(p(MULT_NUM), new MultUnitInputInfo))
        val div_items_ready = Output(Vec(p(DIV_NUM), new DivUnitInputInfo))
    })

    val ldu_items = PipeReg(io.ldu_items, io.stall_from_backpipe, io.flush)
    val stu_items = PipeReg(io.stu_items, io.stall_from_backpipe, io.flush)
    val alu_items = PipeReg(io.alu_items, io.stall_from_backpipe, io.flush)
    val sys_items = PipeReg(io.sys_items, io.stall_from_backpipe, io.flush)

    val results = WireInit(VecInit(
        Seq.fill(p(ALU_NUM) + p(LDU_NUM) + p(SYS_ALU_NUM) + p(MULT_NUM) + p(DIV_NUM))(0.U.asTypeOf(new PRFWriteReq))
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

    for(i <- 0 until p(ALU_NUM)) {
        val ps1HitMask = WireInit(VecInit(
            Seq.fill(p(ALU_NUM) + p(LDU_NUM) + p(SYS_ALU_NUM) + p(MULT_NUM) + p(DIV_NUM))(false.B)
        ))
        val ps2HitMask = WireInit(VecInit(
            Seq.fill(p(ALU_NUM) + p(LDU_NUM) + p(SYS_ALU_NUM) + p(MULT_NUM) + p(DIV_NUM))(false.B)
        ))

        ps1HitMask := VecInit((0 until p(ALU_NUM) + p(LDU_NUM) + p(SYS_ALU_NUM) + p(MULT_NUM) + p(DIV_NUM)).map(
            j => results(j).HasRd && results(j).pd === alu_items(i).ps1 && alu_items(i).HasRs1
        ))

        ps2HitMask := VecInit((0 until p(ALU_NUM) + p(LDU_NUM) + p(SYS_ALU_NUM) + p(MULT_NUM) + p(DIV_NUM)).map(
            j => results(j).HasRd && results(j).pd === alu_items(i).ps2 && alu_items(i).HasRs2
        ))

        val hasPs1Hit = ps1HitMask.asUInt.orR
        val hasPs2Hit = ps2HitMask.asUInt.orR

        val ps1HitIndex = PriorityEncoder(ps1HitMask)
        val ps2HitIndex = PriorityEncoder(ps2HitMask)  

        val is_branch = alu_items(i).opcode === OpcodeVal.BEQ || 
            alu_items(i).opcode === OpcodeVal.JAL ||
            alu_items(i).opcode === OpcodeVal.JALR

        io.integer_inputs(i).data1 := Mux(hasPs1Hit, results(ps1HitIndex).result, io.ps1ALUReadData(i))
        io.integer_inputs(i).data2 := Mux(hasPs2Hit, results(ps2HitIndex).result, io.ps2ALUReadData(i))

        io.integer_inputs(i).funct3 := alu_items(i).funct3
        io.integer_inputs(i).funct7 := alu_items(i).funct7
        io.integer_inputs(i).valid := alu_items(i).HasRd && !is_branch
        io.integer_inputs(i).pc := alu_items(i).pc
        io.integer_inputs(i).is_lui := alu_items(i).opcode === OpcodeVal.LUI
        io.integer_inputs(i).is_auipc := alu_items(i).opcode === OpcodeVal.AUIPC
        io.integer_inputs(i).imm := alu_items(i).imm
        io.integer_inputs(i).is_i_type := alu_items(i).opcode === OpcodeVal.ADDI || 
            alu_items(i).opcode === OpcodeVal.FENCEI

        io.branch_inputs(i).data1 := Mux(hasPs1Hit, results(ps1HitIndex).result, io.ps1ALUReadData(i))
        io.branch_inputs(i).data2 := Mux(hasPs2Hit, results(ps2HitIndex).result, io.ps2ALUReadData(i))
        io.branch_inputs(i).funct3 := alu_items(i).funct3
        io.branch_inputs(i).valid := alu_items(i).valid && is_branch
        io.branch_inputs(i).imm := alu_items(i).imm
        io.branch_inputs(i).is_jal := alu_items(i).opcode === OpcodeVal.JAL
        io.branch_inputs(i).pc := alu_items(i).pc

        io.ps1ALUReadValid(i) := alu_items(i).HasRs1
        io.ps1ALUReadLoc(i) := alu_items(i).ps1

        io.ps2ALUReadValid(i) := alu_items(i).HasRs2
        io.ps2ALUReadLoc(i) := alu_items(i).ps2
    }
    
    for(i <- 0 until p(STU_NUM)) {
        val ps1HitMask = WireInit(VecInit(
            Seq.fill(p(ALU_NUM) + p(LDU_NUM) + p(SYS_ALU_NUM) + p(MULT_NUM) + p(DIV_NUM))(false.B)
        ))
        val ps2HitMask = WireInit(VecInit(
            Seq.fill(p(ALU_NUM) + p(LDU_NUM) + p(SYS_ALU_NUM) + p(MULT_NUM) + p(DIV_NUM))(false.B)
        ))

        ps1HitMask := VecInit((0 until p(ALU_NUM) + p(LDU_NUM) + p(SYS_ALU_NUM) + p(MULT_NUM) + p(DIV_NUM)).map(
            j => results(j).HasRd && results(j).pd === stu_items(i).ps1 && stu_items(i).HasRs1
        ))

        ps2HitMask := VecInit((0 until p(ALU_NUM) + p(LDU_NUM) + p(SYS_ALU_NUM) + p(MULT_NUM) + p(DIV_NUM)).map(
            j => results(j).HasRd && results(j).pd === stu_items(i).ps2 && stu_items(i).HasRs2
        ))

        val hasPs1Hit = ps1HitMask.asUInt.orR
        val hasPs2Hit = ps2HitMask.asUInt.orR

        val ps1HitIndex = PriorityEncoder(ps1HitMask)
        val ps2HitIndex = PriorityEncoder(ps2HitMask)

        io.stu_items_ready(i).ps1_data := Mux(hasPs1Hit, results(ps1HitIndex).result, io.ps1STUReadData(i))
        io.stu_items_ready(i).ps2_data := Mux(hasPs2Hit, results(ps2HitIndex).result, io.ps2STUReadData(i))

        io.ps1STUReadValid(i) := stu_items(i).HasRs1
        io.ps1STUReadLoc(i) := stu_items(i).ps1

        io.ps2STUReadValid(i) := stu_items(i).HasRs2
        io.ps2STUReadLoc(i) := stu_items(i).ps2
    }

    for(i <- 0 until p(LDU_NUM)) {
        val ps1HitMask = WireInit(VecInit(
            Seq.fill(p(ALU_NUM) + p(LDU_NUM) + p(SYS_ALU_NUM) + p(MULT_NUM) + p(DIV_NUM))(false.B)
        ))

        ps1HitMask := VecInit((0 until p(ALU_NUM) + p(LDU_NUM) + p(SYS_ALU_NUM) + p(MULT_NUM) + p(DIV_NUM)).map(
            j => results(j).HasRd && results(j).pd === ldu_items(i).ps1 && ldu_items(i).HasRs1
        ))

        val hasPs1Hit = ps1HitMask.asUInt.orR
        val ps1HitIndex = PriorityEncoder(ps1HitMask)

        io.ldu_items_ready(i).ps1_data := Mux(hasPs1Hit, results(ps1HitIndex).result, io.ps1LDUReadData(i))

        io.ps1LDUReadValid(i) := ldu_items(i).HasRs1
        io.ps1LDUReadLoc(i) := ldu_items(i).ps1      
    }

    for(i <- 0 until p(SYS_ALU_NUM)) {
        val ps1HitMask = WireInit(VecInit(
            Seq.fill(p(ALU_NUM) + p(LDU_NUM) + p(SYS_ALU_NUM) + p(MULT_NUM) + p(DIV_NUM))(false.B)
        ))
        val ps2HitMask = WireInit(VecInit(
            Seq.fill(p(ALU_NUM) + p(LDU_NUM) + p(SYS_ALU_NUM) + p(MULT_NUM) + p(DIV_NUM))(false.B)
        ))

        ps1HitMask := VecInit((0 until p(ALU_NUM) + p(LDU_NUM) + p(SYS_ALU_NUM) + p(MULT_NUM) + p(DIV_NUM)).map(
            j => results(j).HasRd && results(j).pd === sys_items(i).ps1 && sys_items(i).HasRs1
        ))

        ps2HitMask := VecInit((0 until p(ALU_NUM) + p(LDU_NUM) + p(SYS_ALU_NUM) + p(MULT_NUM) + p(DIV_NUM)).map(
            j => results(j).HasRd && results(j).pd === sys_items(i).ps2 && sys_items(i).HasRs2
        ))

        val hasPs1Hit = ps1HitMask.asUInt.orR
        val hasPs2Hit = ps2HitMask.asUInt.orR

        val ps1HitIndex = PriorityEncoder(ps1HitMask)
        val ps2HitIndex = PriorityEncoder(ps2HitMask)

        io.sys_items_ready(i).ps1_data := Mux(hasPs1Hit, results(ps1HitIndex).result, io.ps1SYSEXUReadData(i))
        io.sys_items_ready(i).ps2_data := Mux(hasPs2Hit, results(ps2HitIndex).result, io.ps2SYSEXUReadData(i))

        io.ps1SYSEXUReadValid(i) := sys_items(i).HasRs1
        io.ps1SYSEXUReadLoc(i) := sys_items(i).ps1

        io.ps2SYSEXUReadValid(i) := sys_items(i).HasRs2
        io.ps2SYSEXUReadLoc(i) := sys_items(i).ps2        
    }

    for(i <- 0 until p(ALU_NUM)) {
        io.alu_items_ready(i).valid := alu_items(i).valid
        io.alu_items_ready(i).HasRd := alu_items(i).HasRd
        io.alu_items_ready(i).pd := alu_items(i).pd
        io.alu_items_ready(i).robLoc := alu_items(i).robLoc
        io.alu_items_ready(i).branchPredInfo := alu_items(i).branchPredInfo
    }

    for(i <- 0 until p(LDU_NUM)) {
        io.ldu_items_ready(i).valid := ldu_items(i).valid
        io.ldu_items_ready(i).HasRd := ldu_items(i).HasRd
        io.ldu_items_ready(i).pd := ldu_items(i).pd
        io.ldu_items_ready(i).robLoc := ldu_items(i).robLoc
    }

    for(i <- 0 until p(STU_NUM)) {
        io.stu_items_ready(i).valid := stu_items(i).valid
        io.stu_items_ready(i).HasRd := stu_items(i).HasRd
        io.stu_items_ready(i).pd := stu_items(i).pd
        io.stu_items_ready(i).robLoc := stu_items(i).robLoc
    }

    for(i <- 0 until p(SYS_ALU_NUM)) {
        io.sys_items_ready(i).valid := sys_items(i).valid
        io.sys_items_ready(i).HasRd := sys_items(i).HasRd
        io.sys_items_ready(i).pd := sys_items(i).pd
        io.sys_items_ready(i).robLoc := sys_items(i).robLoc
        io.sys_items_ready(i).branchPredInfo := sys_items(i).branchPredInfo
    }

    if(p(UseRV32IM)) {
        val mult_items = PipeReg(io.mult_items, io.stall_from_backpipe, io.flush)
        val div_items = PipeReg(io.div_items, io.stall_from_backpipe, io.flush)

        for(i <- 0 until p(MULT_NUM)) {
            val idx = i + p(ALU_NUM) + p(LDU_NUM) + p(SYS_ALU_NUM)
            results(idx).HasRd := io.multResult(i).HasRd
            results(idx).pd := io.multResult(i).pd
            results(idx).result := io.multResult(i).result
            results(idx).valid := io.multResult(i).valid        
        }

        for(i <- 0 until p(DIV_NUM)) {
            val idx = i + p(ALU_NUM) + p(LDU_NUM) + p(SYS_ALU_NUM) + p(MULT_NUM)
            results(idx).HasRd := io.divResult(i).HasRd
            results(idx).pd := io.divResult(i).pd
            results(idx).result := io.divResult(i).result
            results(idx).valid := io.divResult(i).valid        
        }

        for(i <- 0 until p(MULT_NUM)) {
            val ps1HitMask = WireInit(VecInit(
                Seq.fill(p(ALU_NUM) + p(LDU_NUM) + p(SYS_ALU_NUM) + p(MULT_NUM) + p(DIV_NUM))(false.B)
            ))
            val ps2HitMask = WireInit(VecInit(
                Seq.fill(p(ALU_NUM) + p(LDU_NUM) + p(SYS_ALU_NUM) + p(MULT_NUM) + p(DIV_NUM))(false.B)
            ))

            ps1HitMask := VecInit((0 until p(ALU_NUM) + p(LDU_NUM) + p(SYS_ALU_NUM) + p(MULT_NUM) + p(DIV_NUM)).map(
                j => results(j).HasRd && results(j).pd === mult_items(i).ps1 && mult_items(i).HasRs1
            ))

            ps2HitMask := VecInit((0 until p(ALU_NUM) + p(LDU_NUM) + p(SYS_ALU_NUM) + p(MULT_NUM) + p(DIV_NUM)).map(
                j => results(j).HasRd && results(j).pd === mult_items(i).ps2 && mult_items(i).HasRs2
            ))

            val hasPs1Hit = ps1HitMask.asUInt.orR
            val hasPs2Hit = ps2HitMask.asUInt.orR

            val ps1HitIndex = PriorityEncoder(ps1HitMask)
            val ps2HitIndex = PriorityEncoder(ps2HitMask)

            io.mult_items_ready(i).data1 := Mux(hasPs1Hit, results(ps1HitIndex).result, io.ps1MULTReadData(i))
            io.mult_items_ready(i).data2 := Mux(hasPs2Hit, results(ps2HitIndex).result, io.ps2MULTReadData(i))

            io.ps1MULTReadValid(i) := mult_items(i).HasRs1
            io.ps1MULTReadLoc(i) := mult_items(i).ps1

            io.ps2MULTReadValid(i) := mult_items(i).HasRs2
            io.ps2MULTReadLoc(i) := mult_items(i).ps2
        }

        for(i <- 0 until p(DIV_NUM)) {
            val ps1HitMask = WireInit(VecInit(
                Seq.fill(p(ALU_NUM) + p(LDU_NUM) + p(SYS_ALU_NUM) + p(MULT_NUM) + p(DIV_NUM))(false.B)
            ))
            val ps2HitMask = WireInit(VecInit(
                Seq.fill(p(ALU_NUM) + p(LDU_NUM) + p(SYS_ALU_NUM) + p(MULT_NUM) + p(DIV_NUM))(false.B)
            ))

            ps1HitMask := VecInit((0 until p(ALU_NUM) + p(LDU_NUM) + p(SYS_ALU_NUM) + p(MULT_NUM) + p(DIV_NUM)).map(
                j => results(j).HasRd && results(j).pd === div_items(i).ps1 && div_items(i).HasRs1
            ))

            ps2HitMask := VecInit((0 until p(ALU_NUM) + p(LDU_NUM) + p(SYS_ALU_NUM) + p(MULT_NUM) + p(DIV_NUM)).map(
                j => results(j).HasRd && results(j).pd === div_items(i).ps2 && div_items(i).HasRs2
            ))

            val hasPs1Hit = ps1HitMask.asUInt.orR
            val hasPs2Hit = ps2HitMask.asUInt.orR

            val ps1HitIndex = PriorityEncoder(ps1HitMask)
            val ps2HitIndex = PriorityEncoder(ps2HitMask)

            io.div_items_ready(i).data1 := Mux(hasPs1Hit, results(ps1HitIndex).result, io.ps1DIVReadData(i))
            io.div_items_ready(i).data2 := Mux(hasPs2Hit, results(ps2HitIndex).result, io.ps2DIVReadData(i))

            io.ps1DIVReadValid(i) := div_items(i).HasRs1
            io.ps1DIVReadLoc(i) := div_items(i).ps1

            io.ps2DIVReadValid(i) := div_items(i).HasRs2
            io.ps2DIVReadLoc(i) := div_items(i).ps2
        }
    }
}