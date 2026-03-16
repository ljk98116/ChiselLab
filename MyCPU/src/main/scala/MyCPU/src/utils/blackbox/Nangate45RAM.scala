package MyCPU.utils

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._

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

class fakeram45_64x7 extends BlackBox with HasBlackBoxPath {
    val io = IO(new Bundle {
        val clk = Input(Bool())
        val addr_in = Input(UInt(6.W))
        val wd_in = Input(UInt(7.W))
        val we_in = Input(Bool())
        val ce_in = Input(Bool())
        val rd_out = Output(UInt(7.W))
        val w_mask_in = Input(UInt(7.W))
    })

    addPath("./MyCPU/src/main/resources/vsrc/utils/pdk/nangate45/blackbox.v")
}

class fakeram45_64x15 extends BlackBox with HasBlackBoxPath {
    val io = IO(new Bundle {
        val clk = Input(Bool())
        val addr_in = Input(UInt(6.W))
        val wd_in = Input(UInt(15.W))
        val we_in = Input(Bool())
        val ce_in = Input(Bool())
        val rd_out = Output(UInt(15.W))
        val w_mask_in = Input(UInt(15.W))
    })

    addPath("./MyCPU/src/main/resources/vsrc/utils/pdk/nangate45/blackbox.v")
}

class fakeram45_64x21 extends BlackBox with HasBlackBoxPath {
    val io = IO(new Bundle {
        val clk = Input(Bool())
        val addr_in = Input(UInt(6.W))
        val wd_in = Input(UInt(21.W))
        val we_in = Input(Bool())
        val ce_in = Input(Bool())
        val rd_out = Output(UInt(21.W))
        val w_mask_in = Input(UInt(21.W))
    })

    addPath("./MyCPU/src/main/resources/vsrc/utils/pdk/nangate45/blackbox.v")
}

class fakeram45_64x32 extends BlackBox with HasBlackBoxPath {
    val io = IO(new Bundle {
        val clk = Input(Bool())
        val addr_in = Input(UInt(6.W))
        val wd_in = Input(UInt(32.W))
        val we_in = Input(Bool())
        val ce_in = Input(Bool())
        val rd_out = Output(UInt(32.W))
        val w_mask_in = Input(UInt(32.W))
    })

    addPath("./MyCPU/src/main/resources/vsrc/utils/pdk/nangate45/blackbox.v")
}

class fakeram45_64x96 extends BlackBox with HasBlackBoxPath {
    val io = IO(new Bundle {
        val clk = Input(Bool())
        val addr_in = Input(UInt(6.W))
        val wd_in = Input(UInt(96.W))
        val we_in = Input(Bool())
        val ce_in = Input(Bool())
        val rd_out = Output(UInt(96.W))
        val w_mask_in = Input(UInt(96.W))
    })

    addPath("./MyCPU/src/main/resources/vsrc/utils/pdk/nangate45/blackbox.v")
}

class fakeram45_256x34 extends BlackBox with HasBlackBoxPath {
    val io = IO(new Bundle {
        val clk = Input(Bool())
        val addr_in = Input(UInt(8.W))
        val wd_in = Input(UInt(34.W))
        val we_in = Input(Bool())
        val ce_in = Input(Bool())
        val rd_out = Output(UInt(34.W))
        val w_mask_in = Input(UInt(34.W))
    })

    addPath("./MyCPU/src/main/resources/vsrc/utils/pdk/nangate45/blackbox.v")
}

class fakeram45_256x95 extends BlackBox with HasBlackBoxPath {
    val io = IO(new Bundle {
        val clk = Input(Bool())
        val addr_in = Input(UInt(8.W))
        val wd_in = Input(UInt(95.W))
        val we_in = Input(Bool())
        val ce_in = Input(Bool())
        val rd_out = Output(UInt(95.W))
        val w_mask_in = Input(UInt(95.W))
    })

    addPath("./MyCPU/src/main/resources/vsrc/utils/pdk/nangate45/blackbox.v")
}

class fakeram45_256x96 extends BlackBox with HasBlackBoxPath {
    val io = IO(new Bundle {
        val clk = Input(Bool())
        val addr_in = Input(UInt(8.W))
        val wd_in = Input(UInt(96.W))
        val we_in = Input(Bool())
        val ce_in = Input(Bool())
        val rd_out = Output(UInt(96.W))
        val w_mask_in = Input(UInt(96.W))
    })

    addPath("./MyCPU/src/main/resources/vsrc/utils/pdk/nangate45/blackbox.v")
}

class fakeram45_512x64 extends BlackBox with HasBlackBoxPath {
    val io = IO(new Bundle {
        val clk = Input(Bool())
        val addr_in = Input(UInt(9.W))
        val wd_in = Input(UInt(64.W))
        val we_in = Input(Bool())
        val ce_in = Input(Bool())
        val rd_out = Output(UInt(64.W))
        val w_mask_in = Input(UInt(64.W))
    })

    addPath("./MyCPU/src/main/resources/vsrc/utils/pdk/nangate45/blackbox.v")
}

class fakeram45_1024x32 extends BlackBox with HasBlackBoxPath {
    val io = IO(new Bundle {
        val clk = Input(Bool())
        val addr_in = Input(UInt(10.W))
        val wd_in = Input(UInt(32.W))
        val we_in = Input(Bool())
        val ce_in = Input(Bool())
        val rd_out = Output(UInt(32.W))
        val w_mask_in = Input(UInt(32.W))
    })

    addPath("./MyCPU/src/main/resources/vsrc/utils/pdk/nangate45/blackbox.v")
}

class fakeram45_2048x39 extends BlackBox with HasBlackBoxPath {
    val io = IO(new Bundle {
        val clk = Input(Bool())
        val addr_in = Input(UInt(11.W))
        val wd_in = Input(UInt(39.W))
        val we_in = Input(Bool())
        val ce_in = Input(Bool())
        val rd_out = Output(UInt(39.W))
        val w_mask_in = Input(UInt(39.W))
    })

    addPath("./MyCPU/src/main/resources/vsrc/utils/pdk/nangate45/blackbox.v")
}

