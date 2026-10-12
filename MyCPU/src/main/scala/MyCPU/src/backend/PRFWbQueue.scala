package MyCPU.backend

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

// per unit wb wait queue
// can impl as lutram to reduce area
class UnitPRFWbQueue[T <: Data](size: Int, gen: T, name: String)(implicit p: Parameters) extends Module {
    val ptrWidth = log2Ceil(size)
    val totalWritePorts = p(ALU_NUM) + p(LDU_NUM) + p(SYS_ALU_NUM) + p(MULT_NUM) +
        p(DIV_NUM) * 2

    assert(gen.typeName == (new ALUResult).typeName ||
        gen.typeName == (new LDUResult).typeName ||
        gen.typeName == (new SysResult).typeName
    )

    val io = IO(new Bundle {
        val flush = Input(Bool())
        val enq = Input(gen)
        val canEnq = Output(Bool())
        val deq = Output(new PRFWriteReq)
        val canDeq = Input(Bool()) // RR controls deq
    })

    val headPtr = RegInit(0.U(ptrWidth.W))
    val tailPtr = RegInit(0.U(ptrWidth.W))

    def valid(x: T): Bool = {
        if(gen.typeName == (new ALUResult).typeName) return x.asTypeOf(new ALUResult).valid
        else if(gen.typeName == (new LDUResult).typeName) return x.asTypeOf(new LDUResult).valid
        else if(gen.typeName == (new SysResult).typeName) return x.asTypeOf(new SysResult).valid
        return false.B
    }

    def HasRd(x: T): Bool = {
        if(gen.typeName == (new ALUResult).typeName) return x.asTypeOf(new ALUResult).HasRd
        else if(gen.typeName == (new LDUResult).typeName) return x.asTypeOf(new LDUResult).HasRd
        else if(gen.typeName == (new SysResult).typeName) return x.asTypeOf(new SysResult).HasRd
        return false.B
    }

    def pd(x: T): UInt = {
        if(gen.typeName == (new ALUResult).typeName) return x.asTypeOf(new ALUResult).pd
        else if(gen.typeName == (new LDUResult).typeName) return x.asTypeOf(new LDUResult).pd
        else if(gen.typeName == (new SysResult).typeName) return x.asTypeOf(new SysResult).pd
        return 0.U
    }

    def rd(x: T): UInt = {
        return 0.U
    }

    def result(x: T): UInt = {
        if(gen.typeName == (new ALUResult).typeName) return x.asTypeOf(new ALUResult).result
        else if(gen.typeName == (new LDUResult).typeName) return x.asTypeOf(new LDUResult).result
        else if(gen.typeName == (new SysResult).typeName) return x.asTypeOf(new SysResult).result
        return 0.U
    }

    if(
        gen.typeName == (new ALUResult).typeName ||
        gen.typeName == (new LDUResult).typeName ||
        gen.typeName == (new SysResult).typeName
    ) {
        val waitQueue = new LargeBank(clock, reset, size, gen, name)    
        
        val deq = WireInit(0.U.asTypeOf(new PRFWriteReq))
        val item = waitQueue.read(headPtr).asTypeOf(gen)

        deq.valid := valid(item)
        deq.pd := pd(item)
        deq.HasRd := HasRd(item)
        deq.rd := rd(item)
        deq.result := result(item)

        val deqItem = WireInit(0.U.asTypeOf(gen))
        deqItem := Mux(headPtr =/= tailPtr, waitQueue.read(headPtr).asTypeOf(gen), 0.U.asTypeOf(gen))

        io.deq.valid := valid(deqItem)
        io.deq.pd := pd(deqItem)
        io.deq.HasRd := HasRd(deqItem)
        io.deq.rd := rd(deqItem)
        io.deq.result := result(deqItem)

        io.canEnq := tailPtr + 1.U =/= headPtr

        when(io.canEnq && valid(io.enq)) {
            waitQueue.write(tailPtr, ~(0.U)(gen.getWidth.W), io.enq.asUInt)
        }

        headPtr := Mux(io.flush, 0.U, headPtr + (io.canDeq && headPtr =/= tailPtr))
        tailPtr := Mux(io.flush, 0.U, tailPtr + (io.canEnq && valid(io.enq)))
    }

}

// used as wb stage
class PRFWbQueue(implicit p: Parameters) extends Module {
    val bankNum = p(PRF_BANKNUM)
    val bankBits = log2Ceil(bankNum)
    
    val totalWritePorts = p(ALU_NUM) + p(LDU_NUM) + p(SYS_ALU_NUM) + p(MULT_NUM) +
        p(DIV_NUM) * 2

    val io = IO(new Bundle {
        val flush = Input(Bool())
        val aluResult = Input(Vec(p(ALU_NUM), new ALUResult))
        val lduResult = Input(Vec(p(LDU_NUM), new LDUResult))
        val sysResult = Input(Vec(p(SYS_ALU_NUM), new SysResult))

        val multResult = Input(Vec(p(MULT_NUM), new ALUResult))
        val divResult = Input(Vec(p(DIV_NUM), new ALUResult))
        val remResult = Input(Vec(p(DIV_NUM), new ALUResult))

        // may stall ex/mem before wb,because prf can not wb now
        val alu_stalls = Output(Vec(p(ALU_NUM), Bool()))
        val ldu_stalls = Output(Vec(p(LDU_NUM), Bool()))
        val sys_stalls = Output(Vec(p(SYS_ALU_NUM), Bool()))
        val mult_stalls = Output(Vec(p(MULT_NUM), Bool()))
        val div_stalls = Output(Vec(p(DIV_NUM), Bool()))
        val rem_stalls = Output(Vec(p(DIV_NUM), Bool()))

        val deqs = Output(Vec(bankNum, new PRFWriteReq))
    })

    // use per unit wb wait queue
    val alu_wait_queues = Seq.tabulate(p(ALU_NUM))(
        q => Module(new UnitPRFWbQueue(4, new ALUResult, "alu_prf_wb_waitqueue_" + q.toString)
    ))
    val ldu_wait_queues = Seq.tabulate(p(LDU_NUM))(
        q => Module(new UnitPRFWbQueue(4, new LDUResult, "ldu_prf_wb_waitqueue_" + q.toString)
    ))
    val sys_wait_queues = Seq.tabulate(p(SYS_ALU_NUM))(
        q => Module(new UnitPRFWbQueue(4, new SysResult, "sys_prf_wb_waitqueue_" + q.toString)
    ))
    val mult_wait_queues = Seq.tabulate(p(MULT_NUM))(
        q => Module(new UnitPRFWbQueue(4, new ALUResult, "mult_prf_wb_waitqueue_" + q.toString)
    ))    
    val div_wait_queues = Seq.tabulate(p(DIV_NUM))(
        q => Module(new UnitPRFWbQueue(4, new ALUResult, "div_prf_wb_waitqueue_" + q.toString))
    )
    val rem_wait_queues = Seq.tabulate(p(DIV_NUM))(
        q => Module(new UnitPRFWbQueue(4, new ALUResult, "rem_prf_wb_waitqueue_" + q.toString))
    )

    val deqs = WireInit(VecInit((0 until totalWritePorts).map(u => {
        if(u < p(ALU_NUM)) {
            val idx = u
            alu_wait_queues(idx).io.deq
        }
        else if(u < p(ALU_NUM) + p(LDU_NUM)) {
            val idx = u - p(ALU_NUM)
            ldu_wait_queues(idx).io.deq            
        }
        else if(u < p(ALU_NUM) + p(LDU_NUM) + p(SYS_ALU_NUM)) {
            val idx = u - p(ALU_NUM) - p(LDU_NUM)
            sys_wait_queues(idx).io.deq            
        }
        else if(u < p(ALU_NUM) + p(LDU_NUM) + p(SYS_ALU_NUM) + p(MULT_NUM)) {
            val idx = u - p(ALU_NUM) - p(LDU_NUM) - p(SYS_ALU_NUM)
            mult_wait_queues(idx).io.deq            
        }
        else if(u < (totalWritePorts - p(DIV_NUM))) {
            val idx = u - (totalWritePorts - p(DIV_NUM) * 2)
            div_wait_queues(idx).io.deq              
        }
        else {
            val idx = u - (totalWritePorts - p(DIV_NUM))
            rem_wait_queues(idx).io.deq
        }
    })))

    val canDeqs = WireInit(VecInit(
        Seq.fill(totalWritePorts)(false.B)
    ))

    // prf wb use RR to choose which unit to wb
    for(b <- 0 until p(PRF_BANKNUM)) {
        val rrCnt = RegInit(0.U(log2Ceil(totalWritePorts).W))
        val deqBankIds = deqs.map(_.pd(bankBits - 1, 0))
        val deqValids = deqs.map(_.valid)

        val bankHitMask = VecInit((0 until totalWritePorts).map(
            i => deqBankIds(i) === b.U && deqValids(i)
        ))

        val rrBankHitMask = (bankHitMask.asUInt >> rrCnt) | ((bankHitMask.asUInt & ((1.U << rrCnt) - 1.U)) << (totalWritePorts.U - rrCnt))
        val hasBankHit = bankHitMask.asUInt.orR
        val rrBankHitIndex = (PriorityEncoder(rrBankHitMask) +& rrCnt)(log2Ceil(totalWritePorts) - 1, 0)

        io.deqs(b) := Mux(hasBankHit, deqs(rrBankHitIndex), 0.U.asTypeOf(new PRFWriteReq))

        canDeqs(rrBankHitIndex) := hasBankHit

        rrCnt := Mux(hasBankHit, rrCnt + 1.U, 0.U)
    }

    for(q <- 0 until p(ALU_NUM)) {
        alu_wait_queues(q).io.flush := io.flush
        alu_wait_queues(q).io.enq := io.aluResult(q)
        val idx = q
        alu_wait_queues(q).io.canDeq := canDeqs(idx)

        io.alu_stalls(q) := !alu_wait_queues(q).io.canEnq
        
    }

    for(q <- 0 until p(LDU_NUM)) {
        ldu_wait_queues(q).io.flush := io.flush
        ldu_wait_queues(q).io.enq := io.lduResult(q)

        val idx = q + p(ALU_NUM)
        ldu_wait_queues(q).io.canDeq := canDeqs(idx)

        io.ldu_stalls(q) := !ldu_wait_queues(q).io.canEnq
    }

    for(q <- 0 until p(SYS_ALU_NUM)) {
        sys_wait_queues(q).io.flush := io.flush
        sys_wait_queues(q).io.enq := io.sysResult(q)

        val idx = q + p(ALU_NUM) + p(LDU_NUM)
        sys_wait_queues(q).io.canDeq := canDeqs(idx)

        io.sys_stalls(q) := !sys_wait_queues(q).io.canEnq
    }

    for(q <- 0 until p(MULT_NUM)) {
        mult_wait_queues(q).io.flush := io.flush
        mult_wait_queues(q).io.enq := io.multResult(q)

        val idx = q + p(ALU_NUM) + p(LDU_NUM) + p(SYS_ALU_NUM)
        mult_wait_queues(q).io.canDeq := canDeqs(idx)

        io.mult_stalls(q) := !mult_wait_queues(q).io.canEnq
    }

    for(q <- 0 until p(DIV_NUM)) {
        div_wait_queues(q).io.flush := io.flush
        div_wait_queues(q).io.enq := io.divResult(q)

        val div_idx = q + p(ALU_NUM) + p(LDU_NUM) + p(SYS_ALU_NUM) + p(MULT_NUM)
        div_wait_queues(q).io.canDeq := canDeqs(div_idx)

        io.div_stalls(q) := !div_wait_queues(q).io.canEnq

        rem_wait_queues(q).io.flush := io.flush
        rem_wait_queues(q).io.enq := io.remResult(q)

        val rem_idx = q + p(ALU_NUM) + p(LDU_NUM) + 
            p(SYS_ALU_NUM) + p(MULT_NUM) + p(DIV_NUM)
        rem_wait_queues(q).io.canDeq := canDeqs(rem_idx)

        io.rem_stalls(q) := !rem_wait_queues(q).io.canEnq        
    }
    

}