package MyCPU.frontend

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._

class IF1Stage(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {

    });

    var pc_reg = RegInit((0.U)(p(ADDR_WIDTH).W));

    pc_reg := pc_reg + (p(FETCH_WIDTH) << 2).U;
}