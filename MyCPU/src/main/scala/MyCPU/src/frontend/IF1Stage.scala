package MyCPU.frontend

import chisel3._
import chisel3.util._

import MyCPU.config._
import MyCPU.io._

class IF1Stage extends Module {
    val io = IO(new Bundle {

    });

    var pc_reg = RegInit((0.U)(CPUConfig.ADDR_WIDTH.W));

    pc_reg := pc_reg + (CPUConfig.FETCH_WIDTH << 2).U;
}