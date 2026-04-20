package MyCPU.backend

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

// p(ALU_NUM) banks, area too large, need opt
// enq prepare stage alloc issue item id and calculate prefixsum
// enq  + select stage 
// enq use prefixSum and choose from current enq Item and uOp skidBuffers, improve IPC, payload avg
// select use current age matrix and rdy state 
// enq stage update iq entries use busy table and wakeup queue deq items(now directly use select res)
// recycle stage(regread stage): write back issued item id to ALUIQfreeIdBuffer
class ALUIQ(implicit p: Parameters) extends Module {
    val totalSize = p(ALUIQ_SIZE)
    val perBankSize = totalSize / p(ALU_NUM)
    val ptrWidth = log2Ceil(totalSize)
    val perBankPtrWith = log2Ceil(perBankSize)
    val bankBits = log2Ceil(p(ALU_NUM))

    val freeIdBuffer = Module(new ALUIQFreeIdBuffer)

    val io = IO(new Bundle {
        val flush = Input(Bool())
        val stall_from_backpipe = Input(Bool())
        val alu_uops = Input(Vec(p(FETCH_WIDTH), new uOp))
        val issue0_stall = Output(Bool())
        // from busytable
        val busytableVec = Input(UInt((1 << p(PREG_WIDTH)).W))
        // to regRead
        val aluiq_o = Output(Vec(p(ALU_NUM), new ALUIQEntry))
    })

    /// issue stage1: enq prepare
    val allocReqs = VecInit(io.alu_uops.map(_.valid))
    val allocResps = freeIdBuffer.io.allocResp

    freeIdBuffer.io.allocReq := allocReqs
    freeIdBuffer.io.flush := io.flush
    freeIdBuffer.io.stall_from_backpipe := io.stall_from_backpipe

    class BufferItem(implicit p: Parameters) extends Bundle with HasValid {
        val uOp = new uOp
        val loc = UInt(ptrWidth.W)
        val valid = Bool()
    }

    val skidBuffers = Seq.fill(p(ALU_NUM))(
        Module(new MultiPortQueueNW1R(p(FETCH_WIDTH), p(FETCH_WIDTH) / p(ALU_NUM), new BufferItem))
    )

    val enqUops = WireInit(VecInit(
        Seq.fill(p(ALU_NUM))(0.U.asTypeOf(new uOp))
    ))
    val enqLocs = WireInit(VecInit(
        Seq.fill(p(ALU_NUM))(0.U(ptrWidth.W))
    ))
    
    val issue1_alu_uops = PipeReg(io.alu_uops, io.stall_from_backpipe, io.flush)
    val issue1_allocResps = PipeReg(allocResps, io.stall_from_backpipe, io.flush)

    val entryRam = 
        RegInit(VecInit(
            Seq.fill(p(ALU_NUM))(
                VecInit(Seq.fill(perBankSize)(0.U.asTypeOf(new ALUIQEntry)))
            )
        ))

    // age(i)(j) = 1 <=> i is older than j
    val ageMats = Seq.fill(p(ALU_NUM))(
        RegInit(VecInit(
            Seq.fill(perBankSize)(
                VecInit(Seq.fill(perBankSize)(
                    false.B
                ))
            )
        ))
    )

    for(b <- 0 until p(ALU_NUM)) {
        val bankHits = VecInit((0 until p(FETCH_WIDTH)).map(
            i => (allocResps(i)(bankBits - 1, 0) === b.U) && io.alu_uops(i).valid
        ))

        val bankPrefixSum = WireInit(VecInit(
            Seq.fill(p(FETCH_WIDTH))(0.U(bankBits.W))
        ))

        val bankHitIndex = PriorityEncoder(bankHits)
        val hasBankHit = WireInit(false.B)

        hasBankHit := bankHits.asUInt.orR

        val useCurrent = WireInit(false.B)
        useCurrent := hasBankHit && !skidBuffers(b).io.deqReady

        bankPrefixSum(0) := 0.U
        for(i <- 1 until p(FETCH_WIDTH)) {
            bankPrefixSum(i) := bankPrefixSum(i-1) + (bankHits(i-1) && !(useCurrent && (i-1).U === bankHitIndex))
        }

        val issue1_useCurrent = PipeReg(useCurrent, io.stall_from_backpipe, io.flush)
        val issue1_bankPrefixSum = PipeReg(bankPrefixSum, io.stall_from_backpipe, io.flush)
        val issue1_skidBufferDeq = PipeReg(skidBuffers(b).io.deq, io.stall_from_backpipe, io.flush)
        val issue1_skidBufferDeqReady = PipeReg(skidBuffers(b).io.deqReady, io.stall_from_backpipe, io.flush)
        val issue1_bankHitIndex = PipeReg(bankHitIndex, io.stall_from_backpipe, io.flush)

        // init
        for(i <- 0 until p(FETCH_WIDTH) / p(ALU_NUM)) {
            skidBuffers(b).io.enq(i) := 0.U.asTypeOf(new BufferItem)
            skidBuffers(b).io.enqValid(i) := false.B
        }

        // cover the old one
        for(i <- 0 until p(FETCH_WIDTH) / p(ALU_NUM)) {
            val bufferItem = WireInit(0.U.asTypeOf(new BufferItem))

            val hitMask = VecInit((0 until p(FETCH_WIDTH)).map(
                j => (issue1_bankPrefixSum(j) === i.U) && issue1_alu_uops(j).valid
            ))
            
            val hitIndex = PriorityEncoder(hitMask)
            val hasHit = hitMask.asUInt.orR

            bufferItem.uOp := issue1_alu_uops(hitIndex)
            bufferItem.loc := issue1_allocResps(hitIndex)
            bufferItem.valid := hasHit

            skidBuffers(b).io.enq(i) := bufferItem
            skidBuffers(b).io.enqValid(i) := bufferItem.valid
        }

        skidBuffers(b).io.deqValid := true.B
        skidBuffers(b).io.flush := io.flush

        enqUops(b) := Mux(
            useCurrent, 
            issue1_alu_uops(issue1_bankHitIndex),
            Mux(issue1_skidBufferDeqReady, issue1_skidBufferDeq.uOp, 0.U.asTypeOf(new uOp))
        )

        enqLocs(b) := Mux(
            useCurrent, 
            issue1_allocResps(issue1_bankHitIndex),
            Mux(issue1_skidBufferDeqReady, issue1_skidBufferDeq.loc, 0.U)            
        )
    }

    // skidBuffer can not be full 
    io.issue0_stall := !freeIdBuffer.io.deqReady 

    // issue1 update entries
    for(b <- 0 until p(ALU_NUM)) {
        // iq entry update
        for(i <- 0 until perBankSize) {
            val aluWakeUp1 = VecInit((0 until p(LDU_NUM)).map(j =>
                io.aluiq_o(j).HasRd && enqUops(b).HasRs1 && io.aluiq_o(j).pd === enqUops(b).ps1
            )).asUInt.orR
            val aluWakeUp2 = VecInit((0 until p(LDU_NUM)).map(j =>
                io.aluiq_o(j).HasRd && enqUops(b).HasRs2 && io.aluiq_o(j).pd === enqUops(b).ps2
            )).asUInt.orR
            when(i.U === enqLocs(b)(ptrWidth - 1, bankBits) && enqUops(b).valid) {

                val iq_entry = WireInit(0.U.asTypeOf(new ALUIQEntry))

                iq_entry.HasRd := enqUops(b).HasRd
                iq_entry.branchPredInfo := enqUops(b).branchPredInfo
                iq_entry.funct3 := enqUops(b).funct3
                iq_entry.funct7 := enqUops(b).funct7
                iq_entry.imm := enqUops(b).imm
                iq_entry.lat := enqUops(b).lat
                iq_entry.old_pd := enqUops(b).old_pd
                iq_entry.opcode := enqUops(b).opcode
                iq_entry.pc := enqUops(b).pc
                iq_entry.pd := enqUops(b).pd
                iq_entry.ps1 := enqUops(b).HasRd
                iq_entry.HasRs1 := enqUops(b).HasRs1
                iq_entry.HasRs2 := enqUops(b).HasRs2
                iq_entry.ps1 := enqUops(b).ps1
                iq_entry.ps2 := enqUops(b).ps2
                iq_entry.rd := enqUops(b).rd
                iq_entry.rdy1 := io.busytableVec(enqUops(b).ps1) || aluWakeUp1
                iq_entry.rdy2 := io.busytableVec(enqUops(b).ps2) || aluWakeUp2
                iq_entry.rs1 := enqUops(b).rs1
                iq_entry.rs2 := enqUops(b).rs2
                iq_entry.valid := enqUops(b).valid

                entryRam(b)(enqLocs(b)(ptrWidth - 1, bankBits)) := iq_entry
            }.otherwise {

                entryRam(b)(i).rdy1 := entryRam(b)(i).rdy1 || aluWakeUp1 || io.busytableVec(entryRam(b)(i).ps1)
                entryRam(b)(i).rdy2 := entryRam(b)(i).rdy2 || aluWakeUp2 || io.busytableVec(entryRam(b)(i).ps2)
            }
        }
    }

    /// select
    val selectReadyVec = RegInit(VecInit(
        Seq.fill(p(ALU_NUM))(
            VecInit(Seq.fill(perBankSize)(false.B))
        )
    ))

    val selectCanIssueVec = RegInit(VecInit(
        Seq.fill(p(ALU_NUM))(
            VecInit(Seq.fill(perBankSize)(false.B))
        )
    ))

    for(b <- 0 until p(ALU_NUM)) {
        for(i <- 0 until perBankSize) {
            selectReadyVec(b)(i) := !(
                (entryRam(b)(i).HasRs1 && !entryRam(b)(i).rdy1) || 
                (entryRam(b)(i).HasRs2 && !entryRam(b)(i).rdy2)
            )
        }

        for(i <- 0 until perBankSize) {
            // have j older than i && j ready, i can not issue
            val canNotIssueVec = WireInit(VecInit(Seq.fill(perBankSize)(false.B)))
            for(j <- 0 until perBankSize) {
                canNotIssueVec(j) := ageMats(b)(j)(i) && selectReadyVec(b)(j)
            }
            selectCanIssueVec(b)(i) := selectReadyVec(b)(i) && !canNotIssueVec.asUInt.orR
        }

        io.aluiq_o(b) := 0.U.asTypeOf(new ALUIQEntry)

        val issueIdx = PriorityEncoder(selectCanIssueVec(b))
        val issueEnable = WireInit(false.B)

        issueEnable := selectCanIssueVec(b).asUInt.orR

        io.aluiq_o(b).valid := issueEnable
        io.aluiq_o(b) := entryRam(b)(issueIdx)

        // recycle stage wb
        freeIdBuffer.io.writeReq(b).valid := PipeReg(issueEnable, io.stall_from_backpipe, io.flush)
        freeIdBuffer.io.writeReq(b).id := PipeReg(issueIdx, io.stall_from_backpipe, io.flush)

        // update age mat
        for(i <- 0 until perBankSize) {
            val needUpdateAgeMatrix = WireInit(false.B)
            needUpdateAgeMatrix := 
                ((i.U === enqLocs(b)(ptrWidth - 1, bankBits)) && enqUops(b).valid) || // enq need update
                selectCanIssueVec(b)(i) // deq need update
            
            // enq item update
            when(needUpdateAgeMatrix) {
                for(j <- 0 until perBankSize) {
                    if(i != j) {
                        ageMats(b)(i)(j) := false.B
                        ageMats(b)(j)(i) := entryRam(b)(j).valid
                    }
                }
            }
        }
    }
}
