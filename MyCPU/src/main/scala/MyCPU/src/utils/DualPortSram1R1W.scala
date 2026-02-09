package MyCPU.utils

import chisel3._
import chisel3.util._

class BlackBoxDualPortSram1R1W(LABEL: String, ADDR_WIDTH: Int, DATA_WIDTH: Int) extends BlackBox(Map(
    "ADDR_WIDTH" -> ADDR_WIDTH,
    "DATA_WIDTH" -> DATA_WIDTH,
    "LABEL"-> LABEL
)) with HasBlackBoxPath{
    val clk = IO(Input(Bool()));
    val rst = IO(Input(Bool()));
    val raddr = IO(Input(UInt(ADDR_WIDTH.W)));
    val waddr = IO(Input(UInt(ADDR_WIDTH.W)));
    val en = IO(Input(Bool()));
    val wen = IO(Input(Bool()));
    val wmask = IO(Input(UInt(DATA_WIDTH.W)));
    val wdata = IO(Input(UInt(DATA_WIDTH.W)));
    val rdata = IO(Output(UInt(DATA_WIDTH.W)));

    addPath("./MyCPU/src/main/resources/vsrc/utils/BlackBoxDualPortSram1R1W.v")
}

class DualPortSram1R1W(LABEL: String, ADDR_WIDTH: Int, DATA_WIDTH: Int) extends Module {

    val io = IO(new Bundle {
        val raddr = Input(UInt(ADDR_WIDTH.W));
        val waddr = Input(UInt(ADDR_WIDTH.W));
        val en = Input(Bool());
        val wen = Input(Bool());
        val wmask = Input(UInt(DATA_WIDTH.W));
        val wdata = Input(UInt(DATA_WIDTH.W));
        val rdata = Output(UInt(DATA_WIDTH.W));
    });

    val internal_ram = Module(new BlackBoxDualPortSram1R1W(LABEL, ADDR_WIDTH, DATA_WIDTH));

    internal_ram.clk := clock.asBool;
    internal_ram.rst := reset.asBool;
    internal_ram.raddr := io.raddr;
    internal_ram.waddr := io.waddr;
    internal_ram.en := io.en;
    internal_ram.wen := io.wen;
    internal_ram.waddr := io.waddr;
    internal_ram.wmask := io.wmask;
    internal_ram.wdata := io.wdata;
    
    io.rdata := internal_ram.rdata;
}


