package MyCPU.backend

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

class STU(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val flush = Input(Bool())
        val stall_from_backpipe = Input(Bool())
        val stu_item = Input(new STUInputInfo)

        val result = Output(new AGUResult)
        val uop = Output(new STUIQEntry) 
    })

    val result = WireInit(0.U.asTypeOf(new AGUResult))

    result.valid := io.stu_item.valid
    result.rd := io.stu_item.uop.rd
    result.pd := io.stu_item.uop.pd
    result.addr := io.stu_item.ps1_data + io.stu_item.uop.imm
    result.wdata := io.stu_item.ps2_data
    result.is_sign_ext := false.B
    result.mask := 0.U
    result.robLoc := io.stu_item.uop.robLoc
    result.pc := io.stu_item.uop.pc
    
    switch(io.stu_item.uop.funct3) {
        is("b000".U) {
            result.mask := "h000000FF".U // SB
        }
        is("b001".U) {
            result.mask := "h0000FFFF".U // SH
        }
        is("b010".U) {
            result.mask := "hFFFFFFFF".U // SW
        }
    }

    io.result := result
    io.uop := io.stu_item.uop
}