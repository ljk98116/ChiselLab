package MyCPU.utils

import chisel3._
import chisel3.util._
import chisel3.BlackBox._
import chisel3.experimental._
import chisel3.experimental.BlackBoxHelpers

class BlackBoxDualPortSram2R1W(LABEL: String, ADDR_WIDTH: Int, DATA_WIDTH: Int) extends ExtModule(Map(
    "ADDR_WIDTH" -> ADDR_WIDTH,
    "DATA_WIDTH" -> DATA_WIDTH,
    "LABEL"-> LABEL
)){

    val io = IO(new Bundle {
        val clk = Input(Bool());
        val rst = Input(Bool());
        val raddr0 = Input(UInt(32.W));
        val raddr1 = Input(UInt(32.W));
        val waddr = Input(UInt(32.W));
        val en = Input(Bool());
        val wen = Input(Bool());
        val wmask = Input(UInt(32.W));
        val wdata = Input(UInt(32.W));
        val rdata0 = Output(UInt(32.W));
        val rdata1 = Output(UInt(32.W));
    })
    addPath("./MyCPU/src/resources/utils/BlackBoxDualPortSram2R1W.v")
}

class DualPortSram2R1W(LABEL: String, ADDR_WIDTH: Int, DATA_WIDTH: Int) extends Module {
    val io = IO(new Bundle {
        val raddr0 = Input(UInt(32.W));
        val raddr1 = Input(UInt(32.W));
        val waddr = Input(UInt(32.W));
        val en = Input(Bool());
        val wen = Input(Bool());
        val wmask = Input(UInt(32.W));
        val wdata = Input(UInt(32.W));
        val rdata0 = Output(UInt(32.W));
        val rdata1 = Output(UInt(32.W));
    });

    val internal_ram = Module(new BlackBoxDualPortSram2R1W(LABEL, ADDR_WIDTH, DATA_WIDTH));

    internal_ram.io.clk := clock.asBool;
    internal_ram.io.rst := reset.asBool;
    internal_ram.io.raddr0 := io.raddr0;
    internal_ram.io.raddr1 := io.raddr1;
    internal_ram.io.waddr := io.waddr;
    internal_ram.io.en := io.en;
    internal_ram.io.wen := io.wen;
    internal_ram.io.waddr := io.waddr;
    internal_ram.io.wmask := io.wmask;
    internal_ram.io.wdata := io.wdata;
    io.rdata0 := internal_ram.io.rdata0;
    io.rdata1 := internal_ram.io.rdata1;
}
