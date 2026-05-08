package MyCPU.backend

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

// Wallace Tree Impl, have pipeline stall, need several cycles to get result
// 6 64bits number add within 1 cycle
// 6 datas -> carry & sum
class CSA4(width: Int)(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val datas  = Input(Vec(4, UInt(width.W)))
        val sum    = Output(UInt(width.W))
        val carry = Output(UInt(width.W))
    })

    // --- 第一级 CSA: 0,1,2 和 3,4,5 ---
    def csa3(a: UInt, b: UInt, c: UInt): (UInt, UInt) = {
        val s = a ^ b ^ c
        val c_out = (a & b) | (a & c) | (b & c)
        (s, c_out << 1.U) // carry 左移一位
    }

    val (s0, c0) = csa3(io.datas(0), io.datas(1), io.datas(2))
    val (s1, c1) = csa3(s0, c0, io.datas(3))

    // 输出
    io.sum := s1
    io.carry := c1
}

class MultUnit(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val flush = Input(Bool())
        val stall_from_backpipe = Input(Bool())

        val valid = Input(Bool())
        val funct3 = Input(UInt(3.W)) 
        val data1 = Input(UInt(p(DATA_WIDTH).W))
        val data2 = Input(UInt(p(DATA_WIDTH).W))
        val result = Output(UInt(p(DATA_WIDTH).W)) 

        val ex_stall = Output(Bool())      
    })

    var dataNum = p(DATA_WIDTH)
    val idle::working::Nil = Enum(2)

    val state = RegInit(idle)
    val nextState = WireInit(idle)

    val cycleCounter = RegInit(0.U(3.W))
    // build Wallace tree 
    // calculate CSA num cycle
    // 32 / 4 = 8 units 
    // 16 / 4 = 4 units
    // 8 / 4 = 2 units
    // 4 / 4 = 1 units
    // add 2 datas
    // at most wait for 4 cycles
    
}