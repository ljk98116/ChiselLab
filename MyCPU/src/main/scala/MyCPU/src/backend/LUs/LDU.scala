package MyCPU.backend

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

class LDU(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val flush = Input(Bool())
        val stall_from_backpipe = Input(Bool())
        val ldu_item = Input(new LDUInputInfo)

        val result = Output(new AGUResult)
        val uop = Output(new LDUIQEntry) 
    })

    val result = WireInit(0.U.asTypeOf(new AGUResult))

    result.valid := io.ldu_item.valid
    result.rd := io.ldu_item.uop.rd
    result.pd := io.ldu_item.uop.pd
    result.addr := io.ldu_item.ps1_data + io.ldu_item.uop.imm
    result.is_sign_ext := !io.ldu_item.uop.funct3(2) // except LBU/LHU
    result.mask := 0.U
    result.wdata := 0.U
    
    switch(io.ldu_item.uop.funct3) {
        is("b000".U, "b100".U) {
            result.mask := "h0000 00FF".U
        }
        is("b001".U, "b101".U) {
            result.mask := "h0000 FFFF".U
        }
        is("b010".U) {
            result.mask := "hFFFF FFFF".U
        }
    }

    io.result := result
    io.uop := io.ldu_item.uop
}