package MyCPU.utils

import chisel3._
import chisel3.util._

class MySyncReadMem extends Module {
    val io = IO(new Bundle {
        val addr = Input(UInt(5.W))         // 5-bit address for read/write
        val writeData = Input(UInt(64.W))   // Data to write (64 bits)
        val writeMask = Input(UInt(64.W))  // Write mask for selective writes
        val Enable = Input(Bool())      // Enable
        val IsWrite = Input(Bool())     // Enable for write operation
        val readData = Output(UInt(64.W))   // Data read from memory (64 bits)
    })

    // Declare a 256x64 bit synchronous read-write memory
    val mem = SyncReadMem(32, Vec(64, Bool()))

    var write_data_bits = WireInit(VecInit(
        Seq.fill(64)(false.B)
    ))
    var read_data_bits = WireInit(VecInit(
        Seq.fill(64)(false.B)
    ))
    var write_mask_bits = WireInit(VecInit(
        Seq.fill(64)(false.B)
    ))

    for(i:Int <- 0 until 64) {
        write_data_bits(i) := io.writeData(i)
        write_mask_bits(i) := io.writeMask(i)
    }

    // Perform write if writeEnable is high
    when(io.IsWrite && io.Enable) {
        mem.write(io.addr, write_data_bits, write_mask_bits)  // Write operation
    }

    // Perform read if readEnable is high (and writeEnable is low)
    read_data_bits := mem.read(io.addr, io.Enable)  // Read operation
    io.readData := read_data_bits.asUInt
}
