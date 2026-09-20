package MyCPU.utils

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

class AXI4Sram (
    depth: Int,
    addrWidth: Int,
    dataWidth: Int,
    idWidth: Int
)(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val axi4_slave = new AXI4(addrWidth, dataWidth, idWidth)
    })

    val mem = new LargeSyncBank(clock, reset, depth, UInt(dataWidth.W), "dut_memory")

    val writeAddr = Reg(UInt(addrWidth.W))
    val writeLen  = Reg(UInt(8.W))
    val writeCnt  = RegInit(0.U(8.W))
    val writeId   = Reg(UInt(idWidth.W))

    val readAddr = Reg(UInt(addrWidth.W))
    val readLen  = Reg(UInt(8.W))
    val readCnt  = RegInit(0.U(8.W))
    val readId   = Reg(UInt(idWidth.W))

    val beatBytes = (dataWidth/8).U    

    // AW
    io.axi4_slave.aw.ready := true.B

    when(io.axi4_slave.aw.fire) {
        writeAddr := io.axi4_slave.aw.bits.addr
        writeLen  := io.axi4_slave.aw.bits.len
        writeCnt  := 0.U
        writeId   := io.axi4_slave.aw.bits.id
    }

    // W
    io.axi4_slave.w.ready := true.B

    when(io.axi4_slave.w.fire) {

        mem.write(
            writeAddr >> log2Ceil(dataWidth/8),
            ~(0.U)(dataWidth.W),
            io.axi4_slave.w.bits.data
        )
        writeAddr := writeAddr + beatBytes
        writeCnt := writeCnt + 1.U
    }

    // B after write, pull up valid
    io.axi4_slave.b.valid := RegNext(io.axi4_slave.w.fire && io.axi4_slave.w.bits.last)

    io.axi4_slave.b.bits.id := writeId
    io.axi4_slave.b.bits.resp := 0.U

    // AR
    io.axi4_slave.ar.ready := true.B

    when(io.axi4_slave.ar.fire) {
        readAddr := io.axi4_slave.ar.bits.addr
        readLen  := io.axi4_slave.ar.bits.len
        readCnt  := 0.U
        readId   := io.axi4_slave.ar.bits.id
    }

    // R
    val rdata = mem.read(
        readAddr >> log2Ceil(dataWidth/8)
    )
    
    io.axi4_slave.r.ready := true.B
    io.axi4_slave.r.valid := true.B

    io.axi4_slave.r.bits.id := readId
    io.axi4_slave.r.bits.data := rdata
    io.axi4_slave.r.bits.resp := 0.U
    io.axi4_slave.r.bits.last := readCnt === readLen

    when(io.axi4_slave.r.fire) {
        readAddr := readAddr + beatBytes
        readCnt := readCnt + 1.U
    }                
}