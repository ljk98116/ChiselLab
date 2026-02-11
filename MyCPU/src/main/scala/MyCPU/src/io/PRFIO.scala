package MyCPU.io

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import MyCPU.config._

class PRFReadReq(implicit p: Parameters) extends Bundle {
    val en = Bool();
    val addr = UInt(p(PREG_WIDTH).W);
}

class PRFReadRes(implicit p: Parameters) extends Bundle {
    val data = UInt(p(DATA_WIDTH).W);
}

class PRFWriteReq(implicit p: Parameters) extends Bundle {
    val en = Bool();
    val addr = UInt(p(PREG_WIDTH).W);
    val mask = UInt(p(DATA_WIDTH).W);
    val data = UInt(p(DATA_WIDTH).W);
}