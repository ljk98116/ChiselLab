package MyCPU.utils

import chisel3._
import chisel3.util._
import MyCPU.io._

import org.chipsalliance.cde.config._

abstract class BaseBank[T <: Data](depth: Int, gen: T) extends Module {
    val io = IO(new Bundle {
        val wen   = Input(Bool())
        val waddr = Input(UInt(log2Ceil(depth).W))
        val wdata = Input(gen)
        val wmask = Input(gen)

        val raddr = Input(UInt(log2Ceil(depth).W))
        val rdata = Output(gen)
    })
}

class SmallBank[T <: Data](depth: Int, gen: T) extends BaseBank(depth, gen) {

    val mem = RegInit(VecInit(Seq.fill(depth)(0.U.asTypeOf(gen))))

    when(io.wen) {
        mem(io.waddr) := io.wdata
    }

    io.rdata := mem(io.raddr)
}

class LargeBank[T <: Data](depth: Int, gen: T) extends BaseBank(depth, gen) {

    val mem = Mem(depth, gen)

    when(io.wen) {
        mem.write(io.waddr, io.wdata)
    }

    io.rdata := mem.read(io.raddr)
}


// only single RW, read write must split
class Nangate45Bank[T <: Data](depth: Int, gen: T, name: String = "")(implicit p: Parameters) extends BaseBank(depth, gen) 
{
    val sram_mappings = Seq(
        (32, 64), (64, 7), (64, 15), (64, 21), (64, 32), (64, 96), (256, 34),
        (256, 95), (256, 96), (512, 64), (1024, 32), (2048, 39)
    )
    val width = gen.getWidth
    require(sram_mappings.contains((depth, width)), s"no suitable ram found for nanage45 for design name: $name, ${depth}, ${width}")

    val idxWidth = log2Ceil(depth)

    // get symbols
    (depth, width) match {
        case (32, 64) => {
            val ram = Module(new fakeram45_32x64)
            ram.io.clk := clock.asBool
            ram.io.addr_in := Mux(io.wen, io.waddr, io.raddr)
            ram.io.ce_in := !reset.asBool
            io.rdata := ram.io.rd_out
            ram.io.w_mask_in := io.wmask
            ram.io.wd_in := io.wdata
            ram.io.we_in := io.wen
        }
        case (64, 7) => {
            val ram = Module(new fakeram45_64x7)
            ram.io.clk := clock.asBool
            ram.io.addr_in := Mux(io.wen, io.waddr, io.raddr)
            ram.io.ce_in := !reset.asBool
            io.rdata := ram.io.rd_out
            ram.io.w_mask_in := io.wmask
            ram.io.wd_in := io.wdata
            ram.io.we_in := io.wen            
        }
        case (64, 15) => {
            val ram = Module(new fakeram45_64x15)
            ram.io.clk := clock.asBool
            ram.io.addr_in := Mux(io.wen, io.waddr, io.raddr)
            ram.io.ce_in := !reset.asBool
            io.rdata := ram.io.rd_out
            ram.io.w_mask_in := io.wmask
            ram.io.wd_in := io.wdata
            ram.io.we_in := io.wen            
        }
        case (64, 21) => {
            val ram = Module(new fakeram45_64x21)
            ram.io.clk := clock.asBool
            ram.io.addr_in := Mux(io.wen, io.waddr, io.raddr)
            ram.io.ce_in := !reset.asBool
            io.rdata := ram.io.rd_out
            ram.io.w_mask_in := io.wmask
            ram.io.wd_in := io.wdata
            ram.io.we_in := io.wen            
        }
        case (64, 32) => {
            val ram = Module(new fakeram45_64x32)
            ram.io.clk := clock.asBool
            ram.io.addr_in := Mux(io.wen, io.waddr, io.raddr)
            ram.io.ce_in := !reset.asBool
            io.rdata := ram.io.rd_out
            ram.io.w_mask_in := io.wmask
            ram.io.wd_in := io.wdata
            ram.io.we_in := io.wen            
        }
        case (64, 96) => {
            val ram = Module(new fakeram45_64x96)
            ram.io.clk := clock.asBool
            ram.io.addr_in := Mux(io.wen, io.waddr, io.raddr)
            ram.io.ce_in := !reset.asBool
            io.rdata := ram.io.rd_out
            ram.io.w_mask_in := io.wmask
            ram.io.wd_in := io.wdata
            ram.io.we_in := io.wen            
        }
        case (256, 34) => {
            val ram = Module(new fakeram45_256x34)
            ram.io.clk := clock.asBool
            ram.io.addr_in := Mux(io.wen, io.waddr, io.raddr)
            ram.io.ce_in := !reset.asBool
            io.rdata := ram.io.rd_out
            ram.io.w_mask_in := io.wmask
            ram.io.wd_in := io.wdata
            ram.io.we_in := io.wen            
        }
        case (256, 95) => {
            val ram = Module(new fakeram45_256x95)
            ram.io.clk := clock.asBool
            ram.io.addr_in := Mux(io.wen, io.waddr, io.raddr)
            ram.io.ce_in := !reset.asBool
            io.rdata := ram.io.rd_out
            ram.io.w_mask_in := io.wmask
            ram.io.wd_in := io.wdata
            ram.io.we_in := io.wen            
        } 
        case (256, 96) => {
            val ram = Module(new fakeram45_256x96)
            ram.io.clk := clock.asBool
            ram.io.addr_in := Mux(io.wen, io.waddr, io.raddr)
            ram.io.ce_in := !reset.asBool
            io.rdata := ram.io.rd_out
            ram.io.w_mask_in := io.wmask
            ram.io.wd_in := io.wdata
            ram.io.we_in := io.wen            
        }
        case (512, 64) => {
            val ram = Module(new fakeram45_512x64)
            ram.io.clk := clock.asBool
            ram.io.addr_in := Mux(io.wen, io.waddr, io.raddr)
            ram.io.ce_in := !reset.asBool
            io.rdata := ram.io.rd_out
            ram.io.w_mask_in := io.wmask
            ram.io.wd_in := io.wdata
            ram.io.we_in := io.wen            
        }   
        case (1024, 32) => {
            val ram = Module(new fakeram45_1024x32)
            ram.io.clk := clock.asBool
            ram.io.addr_in := Mux(io.wen, io.waddr, io.raddr)
            ram.io.ce_in := !reset.asBool
            io.rdata := ram.io.rd_out
            ram.io.w_mask_in := io.wmask
            ram.io.wd_in := io.wdata
            ram.io.we_in := io.wen            
        }  
        case (2048, 39) => {
            val ram = Module(new fakeram45_2048x39)
            ram.io.clk := clock.asBool
            ram.io.addr_in := Mux(io.wen, io.waddr, io.raddr)
            ram.io.ce_in := !reset.asBool
            io.rdata := ram.io.rd_out
            ram.io.w_mask_in := io.wmask
            ram.io.wd_in := io.wdata
            ram.io.we_in := io.wen            
        }          
    }
}