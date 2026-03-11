package MyCPU.cache

import chisel3._
import chisel3.util._

import MyCPU.config._
import MyCPU.io._
import org.chipsalliance.cde.config._

class PreDecode(implicit p: Parameters) extends Module {
    val icacheLineInsts = p(L1CacheLineLength)
    val io = IO(new Bundle {
        val inst = Input(Vec(p(L1CacheLineLength), UInt(p(DATA_WIDTH).W)))
        val icacheBranchMetaInfo = Output(Vec(p(L1CacheLineLength), new ICacheBranchMetaInfo))
    })

    // for RISC-V, call and return can be identified by opcode and funct3
    // call: jal, opcode = 1101111
    // return: jalr x0, x1, 0, opcode = 1100111, funct3 = 000
    // direct jump: jal, opcode = 1101111
    // indirect jump: jalr x0, x1, 0, opcode = 1100111, funct3 = 000
    // conditional branch: opcode = 1100011
    // unconditional branch: jal or jalr x0, x1, 0
    io.icacheBranchMetaInfo := VecInit.fill(p(L1CacheLineLength))(0.U.asTypeOf(new ICacheBranchMetaInfo))
    for(i <- 0 until icacheLineInsts) {
        val opcode = io.inst(i)(6, 0)
        val funct3 = io.inst(i)(14, 12)
        io.icacheBranchMetaInfo(i).isCall := opcode === "b1101111".U
        io.icacheBranchMetaInfo(i).isReturn := opcode === "b1100111".U && funct3 === "b000".U
        io.icacheBranchMetaInfo(i).isDirectJump := opcode === "b1101111".U
        io.icacheBranchMetaInfo(i).isIndirectJump := opcode === "b1100111".U && funct3 === "b000".U
        io.icacheBranchMetaInfo(i).isConditionalBranch := opcode === "b1100011".U
        io.icacheBranchMetaInfo(i).isUnconditionalBranch := opcode === "b1101111".U || (opcode === "b1100111".U && funct3 === "b000".U)
    }
    
}