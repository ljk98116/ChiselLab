package MyCPU.io

import chisel3._
import chisel3.util._

import MyCPU.config._

class PRFReadReq extends Bundle {
    val en = Bool();
    val addr = UInt(CPUConfig.PREG_WIDTH.W);
}

class PRFReadRes extends Bundle {
    val data = UInt(CPUConfig.DATA_WIDTH.W);
}

class PRFWriteReq extends Bundle {
    val en = Bool();
    val addr = UInt(CPUConfig.PREG_WIDTH.W);
    val mask = UInt(CPUConfig.DATA_WIDTH.W);
    val data = UInt(CPUConfig.DATA_WIDTH.W);
}