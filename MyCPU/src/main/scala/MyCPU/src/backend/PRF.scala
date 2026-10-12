package MyCPU.backend

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

// lutram impl
class PRFTable(size: Int, id: Int)(implicit p: Parameters) extends Module {
    val bank_num = p(PRF_BANKNUM)
    val bankBits = log2Ceil(bank_num)
    val io = IO(new Bundle {
        val read_reqs = Input(Vec(2, new PRFReadReq))
        val read_resps = Output(Vec(2, UInt(p(DATA_WIDTH).W)))
        val write_req = Input(new PRFWriteReq)
    })

    val pregfiles = Seq.tabulate(2)(
        i => new LargeBank(clock, reset, size, UInt(p(DATA_WIDTH).W), "prf_bank" + id.toString + "_" + i.toString)
    )

    for(i <- 0 until 2) {
        when(io.read_reqs(i).pd === io.write_req.pd && io.read_reqs(i).valid && io.write_req.valid) {
            io.read_resps(i) := io.write_req.result
        }.otherwise {
            io.read_resps(i) := Mux(io.read_reqs(i).valid, pregfiles(i).read(io.read_reqs(i).pd(p(PREG_WIDTH) - 1, bankBits)), 0.U)
        }

        when(io.write_req.valid) {
            pregfiles(i).write(io.write_req.pd(p(PREG_WIDTH) - 1, bankBits), ~(0.U)(p(DATA_WIDTH).W), io.write_req.result)
        }
    }
}

// readPorts RegFile Table copy to read all reqs at same cycle
// bankNum writePorts, write all tables
class PRF(implicit p: Parameters) extends Module {
    val preg_num = 1 << p(PREG_WIDTH)
    val bank_num = p(PRF_BANKNUM)
    val perBankSize = (preg_num + bank_num - 1) / bank_num

    val io = IO(new Bundle {
        val flush = Input(Bool())

        val prf_read_reqs_1 = Input(Vec(p(PRF_BANKNUM), new PRFReadReq))
        val prf_read_reqs_2 = Input(Vec(p(PRF_BANKNUM), new PRFReadReq))

        val prf_read_resp_1 = Output(Vec(p(PRF_BANKNUM), UInt(p(DATA_WIDTH).W)))
        val prf_read_resp_2 = Output(Vec(p(PRF_BANKNUM), UInt(p(DATA_WIDTH).W)))

        val aluResult = Input(Vec(p(ALU_NUM), new ALUResult))
        val lduResult = Input(Vec(p(LDU_NUM), new LDUResult))
        val sysResult = Input(Vec(p(SYS_ALU_NUM), new SysResult))
        val multResult = Input(Vec(p(MULT_NUM), new ALUResult))
        val divResult = Input(Vec(p(DIV_NUM), new ALUResult))
        val remResult = Input(Vec(p(DIV_NUM), new ALUResult))

        val freeRegIdBufferResp = Input(Vec(p(FETCH_WIDTH), new FreeRegIdBufferReadResp))
        // may account for busytable checkpoint
        val busyTableCheckPoints = Output(Vec(p(FETCH_WIDTH), UInt(preg_num.W)))
        val busyTableCheckPoint = Input(new CheckPointInfo) 
        // busy table io
        val busyTable = Output(UInt((preg_num).W))
        // prf wb queue full
        val stall_wb1_alus = Output(Vec(p(ALU_NUM), Bool()))
        val stall_wb1_ldus = Output(Vec(p(LDU_NUM), Bool()))
        val stall_wb1_sys = Output(Vec(p(SYS_ALU_NUM), Bool()))
        val stall_wb1_mult = Output(Vec(p(MULT_NUM), Bool()))
        val stall_wb1_div = Output(Vec(p(DIV_NUM), Bool()))
        val stall_wb1_rem = Output(Vec(p(DIV_NUM), Bool()))
    })

    val pregs = Seq.tabulate(bank_num)(
        b => Module(new PRFTable(perBankSize, b))
    )

    val prfWbQueue = Module(new PRFWbQueue)

    prfWbQueue.io.aluResult := io.aluResult
    prfWbQueue.io.lduResult := io.lduResult
    prfWbQueue.io.sysResult := io.sysResult
    prfWbQueue.io.multResult := io.multResult
    prfWbQueue.io.divResult := io.divResult
    prfWbQueue.io.remResult := io.remResult
    prfWbQueue.io.flush := io.flush

    io.stall_wb1_alus := prfWbQueue.io.alu_stalls
    io.stall_wb1_ldus := prfWbQueue.io.ldu_stalls
    io.stall_wb1_sys := prfWbQueue.io.sys_stalls
    io.stall_wb1_mult := prfWbQueue.io.mult_stalls
    io.stall_wb1_div := prfWbQueue.io.div_stalls
    io.stall_wb1_rem := prfWbQueue.io.rem_stalls

    for(b <- 0 until bank_num) {
        pregs(b).io.read_reqs(0) := io.prf_read_reqs_1(b)
        pregs(b).io.read_reqs(1) := io.prf_read_reqs_2(b)

        io.prf_read_resp_1(b) := pregs(b).io.read_resps(0)
        io.prf_read_resp_2(b) := pregs(b).io.read_resps(1)

        pregs(b).io.write_req := prfWbQueue.io.deqs(b)
    }
    
    val busyTable = RegInit(0.U(preg_num.W))
    val busyTableAfterRenameVec = Seq.fill(p(FETCH_WIDTH))(
        WireInit(0.U(preg_num.W))
    )

    val busyTableAfterWb = Seq.fill(bank_num)(
        WireInit(0.U(preg_num.W))
    )

    for(i <- 0 until p(FETCH_WIDTH)) {
        busyTableAfterRenameVec(i) := busyTable | (io.freeRegIdBufferResp(i).valid << io.freeRegIdBufferResp(i).preg)
        io.busyTableCheckPoints(i) := busyTableAfterRenameVec(i)
    }

    for(i <- 0 until bank_num) {
        busyTableAfterWb(i) := busyTable ^ (prfWbQueue.io.deqs(i).HasRd << prfWbQueue.io.deqs(i).pd) 
    }

    busyTable := Mux(
        io.flush, io.busyTableCheckPoint.busyTable, 
        busyTableAfterRenameVec.reduce(_|_) | busyTableAfterWb.reduce(_|_)
    )

    io.busyTable := busyTable
}