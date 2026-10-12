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
// snoop alu/ldu/mult/div/rem prf write reqs

// each bank hold an IQEntry
// CAM Impl, update IQEntry using exu/mem result
// 
class RegReadWaitQueue[T <: Data](size: Int, gen: T)(implicit p: Parameters) extends Module {
    val ptrWidth = log2Ceil(size)
    val totalWritePorts = p(ALU_NUM) + p(LDU_NUM) + p(SYS_ALU_NUM) + p(MULT_NUM) +
        p(DIV_NUM) * 2
    val io = IO(new Bundle {
        val flush = Input(Bool())
        val stall = Input(Bool())
        val enq = Input(gen)
        val canEnq = Output(Bool())
        val deq = Output(gen)
        val canDeq = Input(Bool()) // RR controls deq
        val data1 = Output(UInt(p(DATA_WIDTH).W))
        val data2 = Output(UInt(p(DATA_WIDTH).W))
        val rdy1 = Output(Bool())
        val rdy2 = Output(Bool())
        val aluResult = Input(Vec(p(ALU_NUM), new ALUResult))
        val lduResult = Input(Vec(p(LDU_NUM), new LDUResult))
        val sysResult = Input(Vec(p(SYS_ALU_NUM), new SysResult))
        val multResult = Input(Vec(p(MULT_NUM), new ALUResult))
        val divResult = Input(Vec(p(DIV_NUM), new ALUResult))
        val remResult = Input(Vec(p(DIV_NUM), new ALUResult))
    })

    val headPtr = RegInit(0.U(ptrWidth.W))
    val tailPtr = RegInit(0.U(ptrWidth.W))

    class WaitQueueEntry[T1 <: Data](gen: T1)(implicit p: Parameters) extends Bundle {
        val iq_entry = gen
        val data1 = UInt(p(DATA_WIDTH).W)
        val data2 = UInt(p(DATA_WIDTH).W)
        val rdy1 = Bool()
        val rdy2 = Bool()
    }

    val waitQueue = Seq.fill(size)(RegInit(0.U.asTypeOf(new WaitQueueEntry(gen))))

    val nextRdy1 = Seq.fill(size)(WireInit(false.B))
    val nextRdy2 = Seq.fill(size)(WireInit(false.B))

    val nextData1 = Seq.fill(size)(WireInit(0.U(p(DATA_WIDTH).W)))
    val nextData2 = Seq.fill(size)(WireInit(0.U(p(DATA_WIDTH).W)))

    io.deq := 0.U.asTypeOf(gen)
    io.data1 := 0.U
    io.data2 := 0.U
    io.rdy1 := false.B
    io.rdy2 := false.B

    for(i <- 0 until size) {
        when(i.U === headPtr) {
            io.deq := waitQueue(i).iq_entry
            io.data1 := Mux(waitQueue(i).rdy1, waitQueue(i).data1, nextData1(i)) // RAW Update Current
            io.data2 := Mux(waitQueue(i).rdy2, waitQueue(i).data2, nextData2(i)) 
            io.rdy1 := nextRdy1(i) || waitQueue(i).rdy1
            io.rdy2 := nextRdy2(i) || waitQueue(i).rdy2
        }
    }

    io.canEnq := (tailPtr + 1.U) =/= headPtr

    val prf_write_reqs = WireInit(VecInit(
        Seq.fill(totalWritePorts)(0.U.asTypeOf(new PRFWriteReq))
    ))

    for(i <- 0 until totalWritePorts) {
        if(i < p(ALU_NUM)) {
            prf_write_reqs(i).HasRd := io.aluResult(i).HasRd
            prf_write_reqs(i).pd := io.aluResult(i).pd
            prf_write_reqs(i).result := io.aluResult(i).result
            prf_write_reqs(i).rd := 0.U
            prf_write_reqs(i).valid := io.aluResult(i).valid
        }
        else if(i < p(ALU_NUM) + p(LDU_NUM)) {
            val idx = i - p(ALU_NUM)
            prf_write_reqs(i).HasRd := io.lduResult(idx).HasRd
            prf_write_reqs(i).pd := io.lduResult(idx).pd
            prf_write_reqs(i).result := io.lduResult(idx).result
            prf_write_reqs(i).rd := 0.U
            prf_write_reqs(i).valid := io.lduResult(idx).valid
        }
        else if(i < p(ALU_NUM) + p(LDU_NUM) + p(SYS_ALU_NUM)) {
            val idx = i - p(ALU_NUM) - p(LDU_NUM)
            prf_write_reqs(i).HasRd := io.sysResult(idx).HasRd
            prf_write_reqs(i).pd := io.sysResult(idx).pd
            prf_write_reqs(i).result := io.sysResult(idx).result
            prf_write_reqs(i).rd := 0.U
            prf_write_reqs(i).valid := io.sysResult(idx).valid
        }
        else if(i < p(ALU_NUM) + p(LDU_NUM) + p(SYS_ALU_NUM) + p(MULT_NUM)) {
            val idx = i - p(ALU_NUM) - p(LDU_NUM) - p(SYS_ALU_NUM)
            prf_write_reqs(i).HasRd := io.multResult(idx).HasRd
            prf_write_reqs(i).pd := io.multResult(idx).pd
            prf_write_reqs(i).result := io.multResult(idx).result
            prf_write_reqs(i).rd := 0.U
            prf_write_reqs(i).valid := io.multResult(idx).valid
        }        
        else if(i < totalWritePorts - p(DIV_NUM)) {
            val idx = i - p(ALU_NUM) - p(LDU_NUM) - p(SYS_ALU_NUM) - p(MULT_NUM)
            prf_write_reqs(i).HasRd := io.divResult(idx).HasRd
            prf_write_reqs(i).pd := io.divResult(idx).pd
            prf_write_reqs(i).result := io.divResult(idx).result
            prf_write_reqs(i).rd := 0.U
            prf_write_reqs(i).valid := io.divResult(idx).valid
        }
        else if(i < totalWritePorts) {
            val idx = i - p(ALU_NUM) - p(LDU_NUM) - p(SYS_ALU_NUM) - p(MULT_NUM) - p(DIV_NUM)
            prf_write_reqs(i).HasRd := io.remResult(idx).HasRd
            prf_write_reqs(i).pd := io.remResult(idx).pd
            prf_write_reqs(i).result := io.remResult(idx).result
            prf_write_reqs(i).rd := 0.U
            prf_write_reqs(i).valid := io.remResult(idx).valid
        }                 
    }

    for(i <- 0 until size) {
        if(
            waitQueue(i).iq_entry.typeName == (new ALUIQEntry).typeName ||
            waitQueue(i).iq_entry.typeName == (new STUIQEntry).typeName ||
            waitQueue(i).iq_entry.typeName == (new SYSIQEntry).typeName
        ) {
            val ps1 = waitQueue(i).iq_entry.asTypeOf(new ALUIQEntry).ps1
            val ps2 = waitQueue(i).iq_entry.asTypeOf(new ALUIQEntry).ps2
            val HasRs1 = waitQueue(i).iq_entry.asTypeOf(new ALUIQEntry).HasRs1
            val HasRs2 = waitQueue(i).iq_entry.asTypeOf(new ALUIQEntry).HasRs2

            val ps1HitMask = VecInit((0 until totalWritePorts).map(k => 
                prf_write_reqs(k).valid && prf_write_reqs(k).HasRd &&
                prf_write_reqs(k).pd === ps1 && HasRs1    
            ))

            val ps2HitMask = VecInit((0 until totalWritePorts).map(k => 
                prf_write_reqs(k).valid && prf_write_reqs(k).HasRd &&
                prf_write_reqs(k).pd === ps2 && HasRs2    
            ))    

            val ps1Hit = ps1HitMask.asUInt.orR
            val ps1HitIndex = PriorityEncoder(ps1HitMask)

            val ps2Hit = ps1HitMask.asUInt.orR
            val ps2HitIndex = PriorityEncoder(ps2HitMask)   

            nextRdy1(i) := (ps1Hit && HasRs1) || !HasRs1
            nextRdy2(i) := (ps2Hit && HasRs2) || !HasRs2

            when(ps1Hit) {
                nextData1(i) := prf_write_reqs(ps1HitIndex).result
            }

            when(ps2Hit) {
                nextData2(i) := prf_write_reqs(ps2HitIndex).result
            }
        }
        else if(waitQueue(i).iq_entry.typeName == (new LDUIQEntry).typeName) {
            val ps1 = waitQueue(i).iq_entry.asTypeOf(new ALUIQEntry).ps1
            val HasRs1 = waitQueue(i).iq_entry.asTypeOf(new ALUIQEntry).HasRs1

            val ps1HitMask = VecInit((0 until totalWritePorts).map(k => 
                prf_write_reqs(k).valid && prf_write_reqs(k).HasRd &&
                prf_write_reqs(k).pd === ps1 && HasRs1    
            ))    

            val ps1Hit = ps1HitMask.asUInt.orR
            val ps1HitIndex = PriorityEncoder(ps1HitMask)  

            nextRdy1(i) := (ps1Hit && HasRs1) || !HasRs1
            nextRdy2(i) := true.B

            when(ps1Hit) {
                nextData1(i) := prf_write_reqs(ps1HitIndex).result
            }            
        }
    }

    for(i <- 0 until size) {
        when(io.canEnq && i.U === tailPtr) {
            waitQueue(i).iq_entry := io.enq
            waitQueue(i).data1 := 0.U
            waitQueue(i).data2 := 0.U
            waitQueue(i).rdy1 := false.B
            waitQueue(i).rdy2 := false.B
        }
        .elsewhen(io.flush || (i.U === headPtr && !io.stall && io.canDeq)) {
            waitQueue(i) := 0.U.asTypeOf(new WaitQueueEntry(gen))
        }.otherwise {
            waitQueue(i).rdy1 := waitQueue(i).rdy1 || nextRdy1(i)
            waitQueue(i).rdy2 := waitQueue(i).rdy2 || nextRdy2(i)
            waitQueue(i).data1 := Mux(nextRdy1(i), nextData1(i), waitQueue(i).data1)
            waitQueue(i).data2 := Mux(nextRdy2(i), nextData2(i), waitQueue(i).data2)
        }
    }

    tailPtr := Mux(io.flush, 0.U, tailPtr + io.canEnq)
    headPtr := Mux(io.flush, 0.U, headPtr + (!io.stall && io.canDeq))
}

// PRF supports 2 ReadPorts 1 writePort perBank
// PRFBank use copy to impl 2 readPorts, read port 0 bind ps1 , read port 1 bind ps2
class RegReadStage(implicit p: Parameters) extends Module {
    val totalUnits = p(ALU_NUM) + p(LDU_NUM) +p(STU_NUM) + p(SYS_ALU_NUM) + 
        p(MULT_NUM) + p(DIV_NUM) * 2
    
    val totalReadPorts = totalUnits * 2
    val totalPtrWidth = log2Ceil(totalReadPorts)
    val totalWritePorts = p(ALU_NUM) + p(LDU_NUM) + p(SYS_ALU_NUM) + p(MULT_NUM) +
        p(DIV_NUM) * 2
    val bankIdWidth = log2Ceil(p(PRF_BANKNUM))

    val io = IO(new Bundle {
        val flush = Input(Bool())
        // read queue full then stall
        val ldu_items = Input(Vec(p(LDU_NUM), new LDUIQEntry))
        val stu_items = Input(Vec(p(STU_NUM), new STUIQEntry))
        val alu_items = Input(Vec(p(ALU_NUM), new ALUIQEntry))
        val sys_items = Input(Vec(p(SYS_ALU_NUM), new SYSIQEntry))
        val mult_items = Input(Vec(p(MULT_NUM), new ALUIQEntry))
        val div_items = Input(Vec(p(DIV_NUM), new ALUIQEntry))
        val rem_items = Input(Vec(p(DIV_NUM), new ALUIQEntry))

        val stall_alu_iqs = Output(Vec(p(ALU_NUM), Bool()))
        val stall_ldu_iqs = Output(Vec(p(LDU_NUM), Bool()))
        val stall_stu_iqs = Output(Vec(p(STU_NUM), Bool()))
        val stall_sys_iqs = Output(Vec(p(SYS_ALU_NUM), Bool()))
        val stall_mult_iqs = Output(Vec(p(MULT_NUM), Bool()))
        val stall_div_iqs = Output(Vec(p(DIV_NUM), Bool()))
        val stall_rem_iqs = Output(Vec(p(DIV_NUM), Bool()))

        // from exu/mem, current calculate res, used for prewakeup uOps
        val aluResult = Input(Vec(p(ALU_NUM), new ALUResult))
        val lduResult = Input(Vec(p(LDU_NUM), new LDUResult))
        val sysResult = Input(Vec(p(SYS_ALU_NUM), new SysResult))
        val multResult = Input(Vec(p(MULT_NUM), new ALUResult))
        val divResult = Input(Vec(p(DIV_NUM), new ALUResult))
        val remResult = Input(Vec(p(DIV_NUM), new ALUResult))

        // to prf
        val prf_read_reqs_1 = Output(Vec(p(PRF_BANKNUM), new PRFReadReq))
        val prf_read_reqs_2 = Output(Vec(p(PRF_BANKNUM), new PRFReadReq))

        val prf_read_resp_1 = Input(Vec(p(PRF_BANKNUM), UInt(p(DATA_WIDTH).W)))
        val prf_read_resp_2 = Input(Vec(p(PRF_BANKNUM), UInt(p(DATA_WIDTH).W)))

        // to alu sub units
        val integer_inputs = Output(Vec(p(ALU_NUM), new IntegerUnitInputInfo))
        val branch_inputs = Output(Vec(p(ALU_NUM), new BranchUnitInputInfo))
        val mult_inputs = Output(Vec(p(MULT_NUM), new MultUnitInputInfo))
        val div_inputs = Output(Vec(p(DIV_NUM), new DivUnitInputInfo))
        val rem_inputs = Output(Vec(p(DIV_NUM), new DivUnitInputInfo))

        val multReadyVec = Input(Vec(p(MULT_NUM), Bool()))
        val divReadyVec = Input(Vec(p(DIV_NUM), Bool()))
        val remReadyVec = Input(Vec(p(DIV_NUM), Bool()))

        // from prf wb
        val alu_stalls = Input(Vec(p(ALU_NUM), Bool()))
        val ldu_stalls = Input(Vec(p(LDU_NUM), Bool()))
        val sys_stalls = Input(Vec(p(SYS_ALU_NUM), Bool()))
        val mult_stalls = Input(Vec(p(MULT_NUM), Bool()))
        val div_stalls = Input(Vec(p(DIV_NUM), Bool()))
        val rem_stalls = Input(Vec(p(DIV_NUM), Bool())) 
        val mem0_ld_bank_conflict_stalls = Input(Vec(p(LDU_NUM), Bool()))
        val mem0_st_bank_conflict_stalls = Input(Vec(p(STU_NUM), Bool()))

        val alu_items_ready = Output(Vec(p(ALU_NUM), new ALUInputInfo))
        val ldu_items_ready = Output(Vec(p(LDU_NUM), new LDUInputInfo))
        val stu_items_ready = Output(Vec(p(STU_NUM), new STUInputInfo))
        val sys_items_ready = Output(Vec(p(SYS_ALU_NUM), new SYSEXUInputInfo))
        val mult_items_ready = Output(Vec(p(MULT_NUM), new ALUInputInfo))
        val div_items_ready = Output(Vec(p(DIV_NUM), new ALUInputInfo))
        val rem_items_ready = Output(Vec(p(DIV_NUM), new ALUInputInfo))
    })

    // round robin choose exu to access PRF
    // if need access PRF and is alu/mult, need to stall its issue queue, because op data not ready
    val rrCnt = RegInit(0.U(totalReadPorts.W))

    val alu_wait_queues = Seq.fill(p(ALU_NUM))(
        Module(new RegReadWaitQueue(4, new ALUIQEntry))
    )

    val ldu_wait_queues = Seq.fill(p(LDU_NUM))(
        Module(new RegReadWaitQueue(4, new LDUIQEntry))
    )

    val stu_wait_queues = Seq.fill(p(STU_NUM))(
        Module(new RegReadWaitQueue(4, new STUIQEntry))
    )

    val sys_wait_queues = Seq.fill(p(SYS_ALU_NUM))(
        Module(new RegReadWaitQueue(4, new SYSIQEntry))
    )

    val mult_wait_queues = Seq.fill(p(MULT_NUM))(
        Module(new RegReadWaitQueue(4, new ALUIQEntry))
    )

    val div_wait_queues = Seq.fill(p(DIV_NUM))(
        Module(new RegReadWaitQueue(4, new ALUIQEntry))
    )

    val rem_wait_queues = Seq.fill(p(DIV_NUM))(
        Module(new RegReadWaitQueue(4, new ALUIQEntry))
    )

    val deqPs1s = WireInit(VecInit((0 until totalUnits).map(u => {
        if(u < p(ALU_NUM)) {
            val idx = u
            alu_wait_queues(idx).io.deq.ps1
        }
        else if(u < p(ALU_NUM) + p(LDU_NUM)) {
            val idx = u - p(ALU_NUM)
            ldu_wait_queues(idx).io.deq.ps1            
        }
        else if(u < p(ALU_NUM) + p(LDU_NUM) + p(STU_NUM)) {
            val idx = u - p(ALU_NUM) - p(LDU_NUM)
            stu_wait_queues(idx).io.deq.ps1            
        }
        else if(u < p(ALU_NUM) + p(LDU_NUM) + p(STU_NUM) + p(SYS_ALU_NUM)) {
            val idx = u - p(ALU_NUM) - p(LDU_NUM) - p(STU_NUM)
            sys_wait_queues(idx).io.deq.ps1            
        }
        else if(u < p(ALU_NUM) + p(LDU_NUM) + p(STU_NUM) + p(SYS_ALU_NUM) + p(MULT_NUM)) {
            val idx = u - p(ALU_NUM) - p(LDU_NUM) - p(STU_NUM) - p(SYS_ALU_NUM)
            mult_wait_queues(idx).io.deq.ps1            
        }
        else if(u < (totalUnits - p(DIV_NUM))) {
            val idx = u - (totalUnits - p(DIV_NUM) * 2)
            div_wait_queues(idx).io.deq.ps1              
        }
        else {
            val idx = u - (totalUnits - p(DIV_NUM))
            rem_wait_queues(idx).io.deq.ps1
        }
    })))

    val deqPs2s = WireInit(VecInit((0 until totalUnits).map(u => {
        if(u < p(ALU_NUM)) {
            val idx = u
            alu_wait_queues(idx).io.data2
        }
        else if(u < p(ALU_NUM) + p(LDU_NUM)) {
            val idx = u - p(ALU_NUM)
            ldu_wait_queues(idx).io.data2            
        }
        else if(u < p(ALU_NUM) + p(LDU_NUM) + p(STU_NUM)) {
            val idx = u - p(ALU_NUM) - p(LDU_NUM)
            stu_wait_queues(idx).io.data2            
        }
        else if(u < p(ALU_NUM) + p(LDU_NUM) + p(STU_NUM) + p(SYS_ALU_NUM)) {
            val idx = u - p(ALU_NUM) - p(LDU_NUM) - p(STU_NUM)
            sys_wait_queues(idx).io.data2            
        }
        else if(u < p(ALU_NUM) + p(LDU_NUM) + p(STU_NUM) + p(SYS_ALU_NUM) + p(MULT_NUM)) {
            val idx = u - p(ALU_NUM) - p(LDU_NUM) - p(STU_NUM) - p(SYS_ALU_NUM)
            mult_wait_queues(idx).io.data2            
        }
        else if(u < (totalUnits - p(DIV_NUM))) {
            val idx = u - (totalUnits - p(DIV_NUM) * 2)
            div_wait_queues(idx).io.data2              
        }
        else {
            val idx = u - (totalUnits - p(DIV_NUM))
            rem_wait_queues(idx).io.data2
        }
    })))

    val deqData1s = WireInit(VecInit((0 until totalUnits).map(u => {
        if(u < p(ALU_NUM)) {
            val idx = u
            alu_wait_queues(idx).io.data1
        }
        else if(u < p(ALU_NUM) + p(LDU_NUM)) {
            val idx = u - p(ALU_NUM)
            ldu_wait_queues(idx).io.data1            
        }
        else if(u < p(ALU_NUM) + p(LDU_NUM) + p(STU_NUM)) {
            val idx = u - p(ALU_NUM) - p(LDU_NUM)
            stu_wait_queues(idx).io.data1            
        }
        else if(u < p(ALU_NUM) + p(LDU_NUM) + p(STU_NUM) + p(SYS_ALU_NUM)) {
            val idx = u - p(ALU_NUM) - p(LDU_NUM) - p(STU_NUM)
            sys_wait_queues(idx).io.data1            
        }
        else if(u < p(ALU_NUM) + p(LDU_NUM) + p(STU_NUM) + p(SYS_ALU_NUM) + p(MULT_NUM)) {
            val idx = u - p(ALU_NUM) - p(LDU_NUM) - p(STU_NUM) - p(SYS_ALU_NUM)
            mult_wait_queues(idx).io.data1            
        }
        else if(u < (totalUnits - p(DIV_NUM))) {
            val idx = u - (totalUnits - p(DIV_NUM) * 2)
            div_wait_queues(idx).io.data1              
        }
        else {
            val idx = u - (totalUnits - p(DIV_NUM))
            rem_wait_queues(idx).io.data1
        }
    })))

    val deqData2s = WireInit(VecInit((0 until totalUnits).map(u => {
        if(u < p(ALU_NUM)) {
            val idx = u
            alu_wait_queues(idx).io.data2
        }
        else if(u < p(ALU_NUM) + p(LDU_NUM)) {
            val idx = u - p(ALU_NUM)
            ldu_wait_queues(idx).io.data2            
        }
        else if(u < p(ALU_NUM) + p(LDU_NUM) + p(STU_NUM)) {
            val idx = u - p(ALU_NUM) - p(LDU_NUM)
            stu_wait_queues(idx).io.data2            
        }
        else if(u < p(ALU_NUM) + p(LDU_NUM) + p(STU_NUM) + p(SYS_ALU_NUM)) {
            val idx = u - p(ALU_NUM) - p(LDU_NUM) - p(STU_NUM)
            sys_wait_queues(idx).io.data2            
        }
        else if(u < p(ALU_NUM) + p(LDU_NUM) + p(STU_NUM) + p(SYS_ALU_NUM) + p(MULT_NUM)) {
            val idx = u - p(ALU_NUM) - p(LDU_NUM) - p(STU_NUM) - p(SYS_ALU_NUM)
            mult_wait_queues(idx).io.data2            
        }
        else if(u < (totalUnits - p(DIV_NUM))) {
            val idx = u - (totalUnits - p(DIV_NUM) * 2)
            div_wait_queues(idx).io.data2              
        }
        else {
            val idx = u - (totalUnits - p(DIV_NUM))
            rem_wait_queues(idx).io.data2
        }
    })))

    val deqRdy1s = WireInit(VecInit((0 until totalUnits).map(u => {
        if(u < p(ALU_NUM)) {
            val idx = u
            alu_wait_queues(idx).io.rdy1
        }
        else if(u < p(ALU_NUM) + p(LDU_NUM)) {
            val idx = u - p(ALU_NUM)
            ldu_wait_queues(idx).io.rdy1            
        }
        else if(u < p(ALU_NUM) + p(LDU_NUM) + p(STU_NUM)) {
            val idx = u - p(ALU_NUM) - p(LDU_NUM)
            stu_wait_queues(idx).io.rdy1            
        }
        else if(u < p(ALU_NUM) + p(LDU_NUM) + p(STU_NUM) + p(SYS_ALU_NUM)) {
            val idx = u - p(ALU_NUM) - p(LDU_NUM) - p(STU_NUM)
            sys_wait_queues(idx).io.rdy1            
        }
        else if(u < p(ALU_NUM) + p(LDU_NUM) + p(STU_NUM) + p(SYS_ALU_NUM) + p(MULT_NUM)) {
            val idx = u - p(ALU_NUM) - p(LDU_NUM) - p(STU_NUM) - p(SYS_ALU_NUM)
            mult_wait_queues(idx).io.rdy1            
        }
        else if(u < (totalUnits - p(DIV_NUM))) {
            val idx = u - (totalUnits - p(DIV_NUM) * 2)
            div_wait_queues(idx).io.rdy1              
        }
        else {
            val idx = u - (totalUnits - p(DIV_NUM))
            rem_wait_queues(idx).io.rdy1
        }
    })))

    val deqRdy2s = WireInit(VecInit((0 until totalUnits).map(u => {
        if(u < p(ALU_NUM)) {
            val idx = u
            alu_wait_queues(idx).io.rdy2
        }
        else if(u < p(ALU_NUM) + p(LDU_NUM)) {
            val idx = u - p(ALU_NUM)
            ldu_wait_queues(idx).io.rdy2            
        }
        else if(u < p(ALU_NUM) + p(LDU_NUM) + p(STU_NUM)) {
            val idx = u - p(ALU_NUM) - p(LDU_NUM)
            stu_wait_queues(idx).io.rdy2            
        }
        else if(u < p(ALU_NUM) + p(LDU_NUM) + p(STU_NUM) + p(SYS_ALU_NUM)) {
            val idx = u - p(ALU_NUM) - p(LDU_NUM) - p(STU_NUM)
            sys_wait_queues(idx).io.rdy2            
        }
        else if(u < p(ALU_NUM) + p(LDU_NUM) + p(STU_NUM) + p(SYS_ALU_NUM) + p(MULT_NUM)) {
            val idx = u - p(ALU_NUM) - p(LDU_NUM) - p(STU_NUM) - p(SYS_ALU_NUM)
            mult_wait_queues(idx).io.rdy2            
        }
        else if(u < (totalUnits - p(DIV_NUM))) {
            val idx = u - (totalUnits - p(DIV_NUM) * 2)
            div_wait_queues(idx).io.rdy2              
        }
        else {
            val idx = u - (totalUnits - p(DIV_NUM))
            rem_wait_queues(idx).io.rdy2
        }
    })))

    val deqBanks1 = WireInit(VecInit(
        Seq.fill(totalUnits)(0.U(bankIdWidth.W))
    ))

    val prfValids1 = WireInit(VecInit(
        Seq.fill(totalUnits)(false.B)
    ))

    val deqBanks2 = WireInit(VecInit(
        Seq.fill(totalUnits)(0.U(bankIdWidth.W))
    ))

    val prfValids2 = WireInit(VecInit(
        Seq.fill(totalUnits)(false.B)
    ))

    // switch for prf banks
    for(b <- 0 until p(PRF_BANKNUM)) {
        val rrCnt1 = RegInit(0.U(log2Ceil(totalUnits).W))
        val rrCnt2 = RegInit(0.U(log2Ceil(totalUnits).W))

        val bank1s = VecInit(deqPs1s.map(_(bankIdWidth - 1, 0)))
        val bank2s = VecInit(deqPs2s.map(_(bankIdWidth - 1, 0)))

        // need access PRF ?
        val valid1s = VecInit(deqRdy1s.map(!_))
        val valid2s = VecInit(deqRdy2s.map(!_))

        val bankHitMask1 = VecInit((0 until bank1s.length).map(
            i => bank1s(i) === b.U && valid1s(i)
        )).asUInt

        val bankHitMask2 = VecInit((0 until bank2s.length).map(
            i => bank2s(i) === b.U && valid2s(i)
        )).asUInt

        val hasBankHit1 = bankHitMask1.orR
        val hasBankHit2 = bankHitMask2.orR

        val rrBankHitMask1 = (bankHitMask1 >> rrCnt1) | ((bankHitMask1 & (1.U << rrCnt1)) << (log2Ceil(totalUnits).U - rrCnt1))
        val rrBankHitMask2 = (bankHitMask2 >> rrCnt2) | ((bankHitMask2 & (1.U << rrCnt2)) << (log2Ceil(totalUnits).U - rrCnt2))

        val rrBankHitIndex1 = PriorityEncoder(rrBankHitMask1)
        val rrBankHitIndex2 = PriorityEncoder(rrBankHitMask2)

        io.prf_read_reqs_1(b).valid := hasBankHit1
        io.prf_read_reqs_1(b).pd := deqPs1s((rrBankHitIndex1 +& rrCnt1)(log2Ceil(totalUnits) - 1, 0))

        io.prf_read_reqs_2(b).valid := hasBankHit2
        io.prf_read_reqs_2(b).pd := deqPs2s((rrBankHitIndex2 +& rrCnt2)(log2Ceil(totalUnits) - 1, 0))

        deqBanks1((rrBankHitIndex1 +& rrCnt1)(log2Ceil(totalUnits) - 1, 0)) := b.U
        deqBanks2((rrBankHitIndex2 +& rrCnt2)(log2Ceil(totalUnits) - 1, 0)) := b.U

        prfValids1((rrBankHitIndex1 +& rrCnt1)(log2Ceil(totalUnits) - 1, 0)) := hasBankHit1
        prfValids2((rrBankHitIndex2 +& rrCnt2)(log2Ceil(totalUnits) - 1, 0)) := hasBankHit2

        // no high port has bank hit, reset to low port, avoid low port starve too much time
        rrCnt1 := Mux(hasBankHit1, rrCnt1 + 1.U, 0.U)
        rrCnt2 := Mux(hasBankHit2, rrCnt2 + 1.U, 0.U)
    }

    // judge canDeq
    // canDeq = (rdy1 || prf read ps1) && (rdy2 || prf read ps2)

    for(q <- 0 until p(ALU_NUM)) {
        alu_wait_queues(q).io.enq := io.alu_items(q)
        io.stall_alu_iqs(q) := alu_wait_queues(q).io.canEnq

        alu_wait_queues(q).io.flush := io.flush
        alu_wait_queues(q).io.stall := io.stall_alu_iqs(q) || io.alu_stalls(q)
        alu_wait_queues(q).io.aluResult := io.aluResult
        alu_wait_queues(q).io.lduResult := io.lduResult
        alu_wait_queues(q).io.sysResult := io.sysResult
        alu_wait_queues(q).io.multResult := io.multResult
        alu_wait_queues(q).io.divResult := io.divResult
        alu_wait_queues(q).io.remResult := io.remResult

        alu_wait_queues(q).io.canDeq := (deqRdy1s(q) || prfValids1(q)) && (deqRdy2s(q) || prfValids2(q))

        io.alu_items_ready(q).valid := alu_wait_queues(q).io.canDeq
        io.alu_items_ready(q).HasRd := alu_wait_queues(q).io.deq.HasRd
        io.alu_items_ready(q).pd := alu_wait_queues(q).io.deq.pd
        io.alu_items_ready(q).robLoc := alu_wait_queues(q).io.deq.robLoc
        io.alu_items_ready(q).branchPredInfo := alu_wait_queues(q).io.deq.branchPredInfo

        val is_branch = 
            alu_wait_queues(q).io.deq.opcode === OpcodeVal.BEQ || 
            alu_wait_queues(q).io.deq.opcode === OpcodeVal.JALR ||
            alu_wait_queues(q).io.deq.opcode === OpcodeVal.JAL
        
        val idx = q
        io.integer_inputs(q).valid := alu_wait_queues(q).io.canDeq && !is_branch
        io.integer_inputs(q).funct3 := alu_wait_queues(q).io.deq.funct3
        io.integer_inputs(q).funct7 := alu_wait_queues(q).io.deq.funct7
        io.integer_inputs(q).pc := alu_wait_queues(q).io.deq.pc
        io.integer_inputs(q).imm := alu_wait_queues(q).io.deq.imm
        io.integer_inputs(q).is_lui := alu_wait_queues(q).io.deq.opcode === OpcodeVal.LUI
        io.integer_inputs(q).is_auipc := alu_wait_queues(q).io.deq.opcode === OpcodeVal.AUIPC
        io.integer_inputs(q).is_i_type := !(alu_wait_queues(q).io.deq.opcode === OpcodeVal.R)
        io.integer_inputs(q).data1 := Mux(
            prfValids1(idx), io.prf_read_resp_1(deqBanks1(idx)), deqData1s(idx)
        )
        io.integer_inputs(q).data2 := Mux(
            prfValids2(idx), io.prf_read_resp_2(deqBanks2(idx)), deqData2s(idx)
        )

        io.branch_inputs(q).valid := alu_wait_queues(q).io.canDeq && is_branch
        io.branch_inputs(q).is_jal := alu_wait_queues(q).io.deq.opcode === OpcodeVal.JAL
        io.branch_inputs(q).data1 := Mux(
            prfValids1(idx), io.prf_read_resp_1(deqBanks1(idx)), deqData1s(idx)
        )
        io.branch_inputs(q).data2 := Mux(
            prfValids2(idx), io.prf_read_resp_2(deqBanks2(idx)), deqData2s(idx)
        )
        io.branch_inputs(q).funct3 := alu_wait_queues(q).io.deq.funct3
        io.branch_inputs(q).pc := alu_wait_queues(q).io.deq.pc
        io.branch_inputs(q).imm := alu_wait_queues(q).io.deq.imm        
    }

    for(q <- 0 until p(LDU_NUM)) {
        ldu_wait_queues(q).io.enq := io.ldu_items(q)
        io.stall_ldu_iqs(q) := ldu_wait_queues(q).io.canEnq

        ldu_wait_queues(q).io.flush := io.flush
        ldu_wait_queues(q).io.stall := io.stall_ldu_iqs(q) || io.ldu_stalls(q) || io.mem0_ld_bank_conflict_stalls(q)
        ldu_wait_queues(q).io.aluResult := io.aluResult
        ldu_wait_queues(q).io.lduResult := io.lduResult
        ldu_wait_queues(q).io.sysResult := io.sysResult
        ldu_wait_queues(q).io.multResult := io.multResult
        ldu_wait_queues(q).io.divResult := io.divResult
        ldu_wait_queues(q).io.remResult := io.remResult

        val idx = q + p(ALU_NUM)
        ldu_wait_queues(q).io.canDeq := (deqRdy1s(idx) || prfValids1(idx)) && (deqRdy2s(idx) || prfValids2(idx))

        io.ldu_items_ready(q).valid := ldu_wait_queues(q).io.canDeq
        io.ldu_items_ready(q).HasRd := ldu_wait_queues(q).io.deq.HasRd
        io.ldu_items_ready(q).pd := ldu_wait_queues(q).io.deq.pd
        io.ldu_items_ready(q).robLoc := ldu_wait_queues(q).io.deq.robLoc
        io.ldu_items_ready(q).pc := ldu_wait_queues(q).io.deq.pc
        io.ldu_items_ready(q).imm := ldu_wait_queues(q).io.deq.imm
        io.ldu_items_ready(q).funct3 := ldu_wait_queues(q).io.deq.funct3
        io.ldu_items_ready(q).opcode := ldu_wait_queues(q).io.deq.opcode
        io.ldu_items_ready(q).ps1_data := Mux(
            prfValids1(idx), io.prf_read_resp_1(deqBanks1(idx)), deqData1s(idx)
        )
    }

    for(q <- 0 until p(STU_NUM)) {
        stu_wait_queues(q).io.enq := io.stu_items(q)
        io.stall_stu_iqs(q) := stu_wait_queues(q).io.canEnq

        stu_wait_queues(q).io.flush := io.flush
        stu_wait_queues(q).io.stall := io.stall_stu_iqs(q) || io.mem0_st_bank_conflict_stalls(q)
        stu_wait_queues(q).io.aluResult := io.aluResult
        stu_wait_queues(q).io.lduResult := io.lduResult
        stu_wait_queues(q).io.sysResult := io.sysResult
        stu_wait_queues(q).io.multResult := io.multResult
        stu_wait_queues(q).io.divResult := io.divResult
        stu_wait_queues(q).io.remResult := io.remResult

        val idx = q + p(ALU_NUM) + p(LDU_NUM)
        stu_wait_queues(q).io.canDeq := (deqRdy1s(idx) || prfValids1(idx)) && (deqRdy2s(idx) || prfValids2(idx))

        io.stu_items_ready(q).valid := stu_wait_queues(q).io.canDeq
        io.stu_items_ready(q).HasRd := stu_wait_queues(q).io.deq.HasRd
        io.stu_items_ready(q).pd := stu_wait_queues(q).io.deq.pd
        io.stu_items_ready(q).robLoc := stu_wait_queues(q).io.deq.robLoc
        io.stu_items_ready(q).pc := stu_wait_queues(q).io.deq.pc
        io.stu_items_ready(q).imm := stu_wait_queues(q).io.deq.imm
        io.stu_items_ready(q).funct3 := stu_wait_queues(q).io.deq.funct3
        io.stu_items_ready(q).opcode := stu_wait_queues(q).io.deq.opcode
        io.stu_items_ready(q).ps1_data := Mux(
            prfValids1(idx), io.prf_read_resp_1(deqBanks1(idx)), deqData1s(idx)
        )
        io.stu_items_ready(q).ps2_data := Mux(
            prfValids2(idx), io.prf_read_resp_2(deqBanks2(idx)), deqData2s(idx)
        )                 
    }

    for(q <- 0 until p(SYS_ALU_NUM)) {
        sys_wait_queues(q).io.enq := io.sys_items(q)
        io.stall_sys_iqs(q) := sys_wait_queues(q).io.canEnq

        sys_wait_queues(q).io.flush := io.flush
        sys_wait_queues(q).io.stall := io.stall_sys_iqs(q) || io.sys_stalls(q)
        sys_wait_queues(q).io.aluResult := io.aluResult
        sys_wait_queues(q).io.lduResult := io.lduResult
        sys_wait_queues(q).io.sysResult := io.sysResult
        sys_wait_queues(q).io.multResult := io.multResult
        sys_wait_queues(q).io.divResult := io.divResult
        sys_wait_queues(q).io.remResult := io.remResult

        val idx = q + p(ALU_NUM) + p(LDU_NUM) + p(STU_NUM)
        sys_wait_queues(q).io.canDeq := (deqRdy1s(idx) || prfValids1(idx)) && (deqRdy2s(idx) || prfValids2(idx))

        io.sys_items_ready(q).valid := sys_wait_queues(q).io.canDeq
        io.sys_items_ready(q).HasRd := sys_wait_queues(q).io.deq.HasRd
        io.sys_items_ready(q).pd := sys_wait_queues(q).io.deq.pd
        io.sys_items_ready(q).robLoc := sys_wait_queues(q).io.deq.robLoc
        io.sys_items_ready(q).pc := sys_wait_queues(q).io.deq.pc
        io.sys_items_ready(q).imm := sys_wait_queues(q).io.deq.imm
        io.sys_items_ready(q).funct3 := sys_wait_queues(q).io.deq.funct3
        io.sys_items_ready(q).funct7 := sys_wait_queues(q).io.deq.funct7
        io.sys_items_ready(q).branchPredInfo := sys_wait_queues(q).io.deq.branchPredInfo
        io.sys_items_ready(q).opcode := sys_wait_queues(q).io.deq.opcode
        io.sys_items_ready(q).ps1_data := Mux(
            prfValids1(idx), io.prf_read_resp_1(deqBanks1(idx)), deqData1s(idx)
        )
        io.sys_items_ready(q).ps2_data := Mux(
            prfValids2(idx), io.prf_read_resp_2(deqBanks2(idx)), deqData2s(idx)
        ) 
    }

    for(q <- 0 until p(MULT_NUM)) {
        mult_wait_queues(q).io.enq := io.mult_items(q)
        io.stall_mult_iqs(q) := mult_wait_queues(q).io.canEnq

        mult_wait_queues(q).io.flush := io.flush
        mult_wait_queues(q).io.stall := io.stall_mult_iqs(q) || !io.multReadyVec(q) || io.mult_stalls(q)
        mult_wait_queues(q).io.aluResult := io.aluResult
        mult_wait_queues(q).io.lduResult := io.lduResult
        mult_wait_queues(q).io.sysResult := io.sysResult
        mult_wait_queues(q).io.multResult := io.multResult
        mult_wait_queues(q).io.divResult := io.divResult
        mult_wait_queues(q).io.remResult := io.remResult

        val idx = q + p(ALU_NUM) + p(LDU_NUM) + p(STU_NUM) + p(SYS_ALU_NUM)
        mult_wait_queues(q).io.canDeq := (deqRdy1s(idx) || prfValids1(idx)) && (deqRdy2s(idx) || prfValids2(idx))
        io.mult_items_ready(q).valid := mult_wait_queues(q).io.canDeq
        io.mult_items_ready(q).HasRd := mult_wait_queues(q).io.deq.HasRd
        io.mult_items_ready(q).pd := mult_wait_queues(q).io.deq.pd
        io.mult_items_ready(q).robLoc := mult_wait_queues(q).io.deq.robLoc
        io.mult_items_ready(q).branchPredInfo := mult_wait_queues(q).io.deq.branchPredInfo

        io.mult_inputs(q).valid := mult_wait_queues(q).io.canDeq
        io.mult_inputs(q).data1 := Mux(
            prfValids1(idx), io.prf_read_resp_1(deqBanks1(idx)), deqData1s(idx)
        )
        io.mult_inputs(q).data2 := Mux(
            prfValids2(idx), io.prf_read_resp_2(deqBanks2(idx)), deqData2s(idx)
        )
        io.mult_inputs(q).funct3 := mult_wait_queues(q).io.deq.funct3
        io.mult_inputs(q).isW := mult_wait_queues(q).io.deq.opcode === OpcodeVal.MULW
    }

    for(q <- 0 until p(DIV_NUM)) {
        div_wait_queues(q).io.enq := io.div_items(q)
        io.stall_div_iqs(q) := div_wait_queues(q).io.canEnq

        div_wait_queues(q).io.flush := io.flush
        div_wait_queues(q).io.stall := io.stall_div_iqs(q) || !io.divReadyVec(q) || io.div_stalls(q)
        div_wait_queues(q).io.aluResult := io.aluResult
        div_wait_queues(q).io.lduResult := io.lduResult
        div_wait_queues(q).io.sysResult := io.sysResult
        div_wait_queues(q).io.multResult := io.multResult
        div_wait_queues(q).io.divResult := io.divResult
        div_wait_queues(q).io.remResult := io.remResult

        val idx = q + p(ALU_NUM) + p(LDU_NUM) + p(STU_NUM) + p(SYS_ALU_NUM) + p(MULT_NUM)
        div_wait_queues(q).io.canDeq := (deqRdy1s(idx) || prfValids1(idx)) && (deqRdy2s(idx) || prfValids2(idx))

        io.div_items_ready(q).valid := div_wait_queues(q).io.canDeq
        io.div_items_ready(q).HasRd := div_wait_queues(q).io.deq.HasRd
        io.div_items_ready(q).pd := div_wait_queues(q).io.deq.pd
        io.div_items_ready(q).robLoc := div_wait_queues(q).io.deq.robLoc
        io.div_items_ready(q).branchPredInfo := div_wait_queues(q).io.deq.branchPredInfo   

        io.div_inputs(q).valid := div_wait_queues(q).io.canDeq
        io.div_inputs(q).data1 := Mux(
            prfValids1(idx), io.prf_read_resp_1(deqBanks1(idx)), deqData1s(idx)
        )
        io.div_inputs(q).data2 := Mux(
            prfValids2(idx), io.prf_read_resp_2(deqBanks2(idx)), deqData2s(idx)
        )
        io.div_inputs(q).funct3 := div_wait_queues(q).io.deq.funct3
        io.div_inputs(q).isW := div_wait_queues(q).io.deq.opcode === OpcodeVal.MULW
    }

    for(q <- 0 until p(DIV_NUM)) {
        rem_wait_queues(q).io.enq := io.rem_items(q)
        io.stall_rem_iqs(q) := rem_wait_queues(q).io.canEnq

        rem_wait_queues(q).io.flush := io.flush
        rem_wait_queues(q).io.stall := io.stall_rem_iqs(q) || !io.remReadyVec(q) || io. rem_stalls(q)
        rem_wait_queues(q).io.aluResult := io.aluResult
        rem_wait_queues(q).io.lduResult := io.lduResult
        rem_wait_queues(q).io.sysResult := io.sysResult
        rem_wait_queues(q).io.multResult := io.multResult
        rem_wait_queues(q).io.divResult := io.divResult
        rem_wait_queues(q).io.remResult := io.remResult

        val idx = q + p(ALU_NUM) + p(LDU_NUM) + p(STU_NUM) + p(SYS_ALU_NUM) + p(MULT_NUM) + p(DIV_NUM)
        rem_wait_queues(q).io.canDeq := (deqRdy1s(idx) || prfValids1(idx)) && (deqRdy2s(idx) || prfValids2(idx))  
        io.rem_items_ready(q).valid := rem_wait_queues(q).io.canDeq
        io.rem_items_ready(q).HasRd := rem_wait_queues(q).io.deq.HasRd
        io.rem_items_ready(q).pd := rem_wait_queues(q).io.deq.pd
        io.rem_items_ready(q).robLoc := rem_wait_queues(q).io.deq.robLoc
        io.rem_items_ready(q).branchPredInfo := rem_wait_queues(q).io.deq.branchPredInfo

        io.rem_inputs(q).valid := rem_wait_queues(q).io.canDeq
        io.rem_inputs(q).data1 := Mux(
            prfValids1(idx), io.prf_read_resp_1(deqBanks1(idx)), deqData1s(idx)
        )
        io.rem_inputs(q).data2 := Mux(
            prfValids2(idx), io.prf_read_resp_2(deqBanks2(idx)), deqData2s(idx)
        )
        io.rem_inputs(q).funct3 := rem_wait_queues(q).io.deq.funct3
        io.rem_inputs(q).isW := rem_wait_queues(q).io.deq.opcode === OpcodeVal.MULW
    }
}