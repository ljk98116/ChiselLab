package MyCPU.utils

import chisel3._
import chisel3.util._

class BlackBoxDualPortSram2R1W(LABEL: String, ADDR_WIDTH: Int, DATA_WIDTH: Int) extends ExtModule(Map(
    "ADDR_WIDTH" -> ADDR_WIDTH,
    "DATA_WIDTH" -> DATA_WIDTH,
    "LABEL"-> LABEL
)){
    val clk = IO(Input(Bool()));
    val rst = IO(Input(Bool()));
    val raddr0 = IO(Input(UInt(ADDR_WIDTH.W)));
    val raddr1 = IO(Input(UInt(ADDR_WIDTH.W)));
    val waddr = IO(Input(UInt(ADDR_WIDTH.W)));
    val en0 = IO(Input(Bool()));
    val en1 = IO(Input(Bool()));
    val wen = IO(Input(Bool()));
    val wmask = IO(Input(UInt(DATA_WIDTH.W)));
    val wdata = IO(Input(UInt(DATA_WIDTH.W)));
    val rdata0 = IO(Output(UInt(DATA_WIDTH.W)));
    val rdata1 = IO(Output(UInt(DATA_WIDTH.W)));

    addPath("./MyCPU/src/resources/utils/BlackBoxDualPortSram2R1W.v")
}

class DualPortSram2R1W(LABEL: String, ADDR_WIDTH: Int, DATA_WIDTH: Int) extends Module {

    val io = IO(new Bundle {
        val raddr0 = Input(UInt(ADDR_WIDTH.W));
        val raddr1 = Input(UInt(ADDR_WIDTH.W));
        val waddr = Input(UInt(ADDR_WIDTH.W));
        val en0 = Input(Bool());
        val en1 = Input(Bool());
        val wen = Input(Bool());
        val wmask = Input(UInt(DATA_WIDTH.W));
        val wdata = Input(UInt(DATA_WIDTH.W));
        val rdata0 = Output(UInt(DATA_WIDTH.W));
        val rdata1 = Output(UInt(DATA_WIDTH.W));
    });

    val internal_ram = Module(new BlackBoxDualPortSram2R1W(LABEL, ADDR_WIDTH, DATA_WIDTH));

    internal_ram.clk := clock.asBool;
    internal_ram.rst := reset.asBool;
    internal_ram.raddr0 := io.raddr0;
    internal_ram.raddr1 := io.raddr1;
    internal_ram.waddr := io.waddr;
    internal_ram.en0 := io.en0;
    internal_ram.en1 := io.en1;
    internal_ram.wen := io.wen;
    internal_ram.waddr := io.waddr;
    internal_ram.wmask := io.wmask;
    internal_ram.wdata := io.wdata;
    
    io.rdata0 := internal_ram.rdata0;
    io.rdata1 := internal_ram.rdata1;
}
