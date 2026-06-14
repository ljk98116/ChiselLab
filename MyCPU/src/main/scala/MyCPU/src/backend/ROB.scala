package MyCPU.backend

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

// dispatch1 enq 
// use rdy/valid as regs
// rob full will stall at rename1
// rob empty, not commit
// real rob not save results
class ROB(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val flush = Input(Bool())
        val dispatch1_uops = Input(Vec(p(FETCH_WIDTH), new uOp))
        val inorder_locs = Output(Vec(p(FETCH_WIDTH), UInt(p(ROBID_WIDTH).W)))
        // from CDB
        val aluResult = Input(Vec(p(ALU_NUM), new ALUResult))
        val lduResult = Input(Vec(p(LDU_NUM), new LDUResult))
        val sysResult = Input(Vec(p(SYS_ALU_NUM), new SysResult))

        val robHeadPtr = Output(UInt(p(ROBID_WIDTH).W))
        val robTailPtr = Output(UInt(p(ROBID_WIDTH).W))
        val commit_uops = Output(Vec(p(FETCH_WIDTH), new uOp))
        val commit_valid = Output(Bool())
    })

    val size = 1 << p(ROBID_WIDTH)
    val ptrWidth = p(ROBID_WIDTH)
    val bankBits = log2Ceil(p(FETCH_WIDTH))
    val perBankSize = size / p(FETCH_WIDTH)
    val bankNum = p(FETCH_WIDTH)

    val rdyBanks = Seq.fill(p(FETCH_WIDTH))(
        RegInit(0.U(size.W))
    )

    val validBanks = Seq.fill(p(FETCH_WIDTH))(
        RegInit(VecInit(
            Seq.fill(size)(false.B)
        ))
    )

    val uOpBanks = Seq.fill(p(FETCH_WIDTH))(
        SyncReadMem(perBankSize, new uOp)
    )

    // to do: for debug, use C++ memory to handle the result update

    val robHeadPtr = RegInit(0.U(ptrWidth.W))
    val robTailPtr = RegInit(0.U(ptrWidth.W))

    val headRow = robHeadPtr(p(ROBID_WIDTH) - 1, bankBits)
    val canCommit = WireInit(false.B)
    val isEmpty = WireInit(true.B)
    val headRdyMask = WireInit(VecInit(
        Seq.fill(p(FETCH_WIDTH))(false.B)
    ))

    for(i <- 0 until p(FETCH_WIDTH)) {
        val writeIdx = robTailPtr + i.U
        val writeBankId = writeIdx(bankBits - 1, 0)
        val writeBankRow = writeIdx(p(ROBID_WIDTH) - 1, bankBits)

        for(b <- 0 until p(FETCH_WIDTH)) {
            when(b.U === writeBankId) {
                uOpBanks(b).write(writeBankRow, io.dispatch1_uops(i))
                validBanks(b)(writeBankRow) := io.dispatch1_uops(i).valid
            }
        }
    }

    // update rdy vec
    // read head every period
    // head acc when all banks are ready, when head acc clear the rdy bits of all banks
    val rdyUpdateMask = WireInit(0.U(size.W))
    val rdyCommitMask = WireInit(VecInit(
        Seq.fill(bankNum)(0.U(perBankSize.W))
    ))

    val aluRdyUpdateMaskVec = WireInit(VecInit(
        Seq.fill(p(ALU_NUM))(0.U(size.W))
    ))

    val lduRdyUpdateMaskVec = WireInit(VecInit(
        Seq.fill(p(LDU_NUM))(0.U(size.W))
    ))

    val sysRdyUpdateMaskVec = WireInit(VecInit(
        Seq.fill(p(SYS_ALU_NUM))(0.U(size.W))
    ))

    for(i <- 0 until p(ALU_NUM)) {
        aluRdyUpdateMaskVec(i) := io.aluResult(i).valid << io.aluResult(i).robLoc
    }

    for(i <- 0 until p(LDU_NUM)) {
        lduRdyUpdateMaskVec(i) := io.lduResult(i).valid << io.aluResult(i).robLoc
    }

    for(i <- 0 until p(SYS_ALU_NUM)) {
        sysRdyUpdateMaskVec(i) := io.sysResult(i).valid << io.sysResult(i).robLoc
    }

    val aluRdyUpdateMask = WireInit(0.U(size.W))
    aluRdyUpdateMask := 
        (0 until bankNum).map(i => aluRdyUpdateMaskVec(i)).reduce(_|_)

    val lduRdyUpdateMask = WireInit(0.U(size.W))
    lduRdyUpdateMask := 
        (0 until bankNum).map(i => lduRdyUpdateMaskVec(i)).reduce(_|_)

    val sysRdyUpdateMask = WireInit(0.U(size.W))
    sysRdyUpdateMask := 
        (0 until bankNum).map(i => sysRdyUpdateMaskVec(i)).reduce(_|_)    

    rdyUpdateMask := 
        Cat((0 until bankNum).reverse.map(i => rdyBanks(i).asUInt)) | 
        aluRdyUpdateMask |
        lduRdyUpdateMask |
        sysRdyUpdateMask
    
    for(b <- 0 until bankNum) {
        // mark the rdy = true headRow false, others not change
        rdyCommitMask(b) := ~(rdyBanks(b)(headRow) << headRow)
        rdyBanks(b) := Mux(
            io.flush, 
            0.U, 
            rdyBanks(b).asUInt | rdyUpdateMask(perBankSize * (b + 1) - 1, perBankSize * b) & rdyCommitMask(b)
        )
    }

    for(b <- 0 until p(FETCH_WIDTH)) {
        headRdyMask(b) := rdyBanks(b)(headRow) | !validBanks(b)(headRow)
    }

    isEmpty := robHeadPtr === robTailPtr
    canCommit := !isEmpty && headRdyMask.asUInt.andR

    robTailPtr := 
        Mux(
            io.flush, 
            0.U,
            Mux(io.dispatch1_uops.map(_.valid).reduce(_||_), robTailPtr + p(FETCH_WIDTH).U, robTailPtr)
        )
        
    robHeadPtr := 
        Mux(
            io.flush,
            0.U,
            Mux(canCommit, robHeadPtr + p(FETCH_WIDTH).U, robHeadPtr)
        )


    val robHeadUops = WireInit(VecInit(
        Seq.fill(p(FETCH_WIDTH))(0.U.asTypeOf(new uOp))
    ))

    for(b <- 0 until p(FETCH_WIDTH)) {
        robHeadUops(b) := uOpBanks(b).read(headRow)
    }

    io.commit_uops := robHeadUops
    io.commit_valid := canCommit

    io.robHeadPtr := robHeadPtr
    io.robTailPtr := robTailPtr

    for(b <- 0 until bankNum) {
        io.inorder_locs(b) := robTailPtr + b.U
    }
}


