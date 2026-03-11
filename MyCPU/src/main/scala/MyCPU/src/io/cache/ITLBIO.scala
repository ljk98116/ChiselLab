package MyCPU.io

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._

class ITLBEntry(implicit p: Parameters) extends Bundle with HasValid {
    def getVPNWidth = p(ADDR_WIDTH) - 12
    def getPPNWidth = (new PhysicalAddress).GetWidth - 12
    val valid = Bool()
    val tag = UInt(getVPNWidth.W)
    val ppn = UInt(getPPNWidth.W)
    val perm = UInt((new Perm).getWidth.W)
}

class ITLBReqInfo(implicit p: Parameters) extends Bundle with HasValid {
    val addr = UInt(p(ADDR_WIDTH).W)
    val valid = Bool()
}

class ITLBRespInfo(implicit p: Parameters) extends Bundle with HasValid {
    val paddr = new PhysicalAddress
    val valid = Bool()
    val hit = Bool()
    // val exceptionType = new CPUException
}

class ITLBUpdateInfo(implicit p: Parameters) extends Bundle with HasValid {
    val pte = new PTE
    val va = new VirtualAddress
    val valid = Bool()
}

class ITLBRefillReq(implicit p: Parameters) extends Bundle with HasValid {
    val va = new VirtualAddress
    val valid = Bool()
}