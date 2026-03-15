package MyCPU.utils

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._

import scala.reflect.runtime.universe._

class fakeram45_32x64 extends BlackBox with HasBlackBoxPath {
    val io = IO(new Bundle {
        val clk = Input(Bool())
        val addr_in = Input(UInt(5.W))
        val wd_in = Input(UInt(64.W))
        val we_in = Input(Bool())
        val ce_in = Input(Bool())
        val rd_out = Output(UInt(64.W))
        val w_mask_in = Input(UInt(64.W))
    })

    addPath("./MyCPU/src/main/resources/vsrc/utils/pdk/nangate45/blackbox.v")
}

// use reflection
class Nangate45RAM(depth: Int, width: Int, name: String = "")(implicit p: Parameters) extends Module 
{
    val sram_mappings = Map(
        (32, 64), (64, 7), (64, 15), (64, 21), (64, 32), (64, 96), (256, 34),
        (256, 95), (256, 96), (512, 64), (1024, 32), (2048, 39)
    )

    def requirement(kV:(Int, Int)): Boolean = (kV._1 == depth && kV._2 == width).asInstanceOf[Boolean]
    require(sram_mappings.count(requirement) > 0, s"no suitable ram found for nanage45 for design name: $name")

    val idxWidth = log2Ceil(depth)

    val io = IO(new Bundle {
        val addr = Input(UInt(idxWidth.W))
        val wdata = Input(UInt(width.W))
        val wen = Input(Bool())
        val en = Input(Bool())
        val rdata = Output(UInt(width.W))
        val wmask = Input(UInt(width.W))
    })

    // get symbols
    (depth, width) match {
        case (32, 64) => {
            val ram = Module(new fakeram45_32x64)
            ram.io.clk := clock.asBool
            ram.io.addr_in := io.addr
            ram.io.ce_in := io.en
            io.rdata := ram.io.rd_out
            ram.io.w_mask_in := io.wmask
            ram.io.wd_in := io.wdata
            ram.io.we_in := io.wen
        }
    }
}

