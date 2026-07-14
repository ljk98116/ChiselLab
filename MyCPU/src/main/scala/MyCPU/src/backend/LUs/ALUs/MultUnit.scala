package MyCPU.backend

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

// Wallace Tree x Booth 2 bits Impl, have pipeline stall, need several cycles to get result
// 6 64bits number add within 1 cycle
// 6 datas -> carry & sum
class CSA3(width: Int)(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val datas  = Input(Vec(3, UInt(width.W)))
        val sum    = Output(UInt(width.W))
        val carry = Output(UInt(width.W))
    })

    def csa3(a: UInt, b: UInt, c: UInt): (UInt, UInt) = {
        var sum = a ^ b ^ c
        var carry = (a & b) | (a & c) | (b & c)
        (sum, Cat(carry, false.B)) 
    }

    val (sum, carry) = csa3(io.datas(0), io.datas(1), io.datas(2))

    io.sum := sum
    io.carry := carry
}

// get P
class BoothMult2Bits(width: Int)(implicit p : Parameters) extends Module {
    val io = IO(new Bundle {
        val en = Input(Bool())
        val x = Input(UInt(width.W))
        val y = Input(Bool())
        val y_add = Input(Bool())
        val y_sub = Input(Bool())

        val pp = Output(UInt(width.W))
        val c  = Output(Bool())
    })

    val selNeg       =  io.y_add && (io.y ^ io.y_sub)        // -x
    val selPos       = !io.y_add && (io.y ^ io.y_sub)        // +x
    val selDoubleNeg =  io.y_add && !io.y && !io.y_sub       // -2x
    val selDoublePos = !io.y_add &&  io.y &&  io.y_sub       // +2x

    val useNeg    = selNeg || selDoubleNeg
    val useDouble = selDoubleNeg || selDoublePos
    val nonZero   = selNeg || selPos || selDoubleNeg || selDoublePos

    val x1 = io.x
    val x2 = Cat(io.x(width - 2, 0), false.B)

    val abs = Mux(useDouble, x2, x1)

    io.pp := Mux(
        io.en && nonZero,
        Mux(useNeg, ~abs, abs),
        0.U
    )

    io.c := Mux(
        io.en && useNeg,
        1.U,
        0.U
    )
}

// non pipeline version
class MultUnit(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val flush = Input(Bool())
        val stall_from_backpipe = Input(Bool())
        val mult_input_info = Input(new MultUnitInputInfo)

        val result = Output(UInt(p(DATA_WIDTH).W))
        val multReady = Output(Bool())      
    })
    // at most 17 68 bits number, now use 18 datas
    val adderNum = ((p(DATA_WIDTH) + 2) / 2 + 3 - 1) / 3
    val boothNum = ((p(DATA_WIDTH) + 2) / 2 + 3 - 1) / 3 * 3

    // decode Funct3
    val sign_ext_d1 = WireInit(false.B)
    val sign_ext_d2 = WireInit(false.B)

    sign_ext_d1 := io.mult_input_info.funct3 =/= Funct3Val.MULHU // MUL/MULH/MULHSU
    sign_ext_d2 := !io.mult_input_info.funct3(1) // MUL/MULH

    // ext d1 and d2
    val sign_ext_data1 = RegInit(0.U(((p(DATA_WIDTH) + 2) * 2).W))
    val sign_ext_data2 = RegInit(0.U((p(DATA_WIDTH) + 2).W))

    val cycleCounter = RegInit(0.U(4.W))
    // judge and ext data
    sign_ext_data1 := 
        Cat(
            Fill(p(DATA_WIDTH) + 3, sign_ext_d1 && io.mult_input_info.data1(p(DATA_WIDTH) - 1)),
            io.mult_input_info.data1,
            false.B
        )
    sign_ext_data2 := 
        Cat(
            sign_ext_d2 && io.mult_input_info.data2(p(DATA_WIDTH) - 1), 
            io.mult_input_info.data2, 
            false.B
        )

    // 17 68bits number
    val boothP = Seq.fill(boothNum)(
        RegInit(0.U(((p(DATA_WIDTH) + 2) * 2).W))
    )

    // 17 68bits numbers, 17bits c
    // left shift X 2 bits each iter
    // cycle 0 get 17 68bits numbers, 17 bits carry
    val booth2BitModules = Seq.fill((p(DATA_WIDTH) + 2) / 2 + 1)(
        Module(new BoothMult2Bits((p(DATA_WIDTH) + 2) * 2))
    )

    // cycle1: get boothP and boothC
    for(i <- 0 until(p(DATA_WIDTH) + 2, 2)) {
        val realIdx = i + 1

        booth2BitModules(i / 2).io.en := io.mult_input_info.valid && cycleCounter === 1.U
        booth2BitModules(i / 2).io.x := Cat(sign_ext_data1((p(DATA_WIDTH) + 2) * 2 - 1 - i, 0), 0.U(i.W))  // sign_ext_data1 << i.U
        
        booth2BitModules(i / 2).io.y_sub := sign_ext_data2(realIdx - 1)
        booth2BitModules(i / 2).io.y := sign_ext_data2(realIdx)
        if(realIdx + 1 < p(DATA_WIDTH) + 2) {
            booth2BitModules(i / 2).io.y_add := sign_ext_data2(realIdx + 1)
        }
        else {
            booth2BitModules(i / 2).io.y_add := sign_ext_data2(p(DATA_WIDTH) + 1)
        }
    }

    booth2BitModules(boothNum - 1).io.en := false.B;
    booth2BitModules(boothNum - 1).io.x := 0.U
    booth2BitModules(boothNum - 1).io.y := false.B
    booth2BitModules(boothNum - 1).io.y_sub := false.B
    booth2BitModules(boothNum - 1).io.y_add := false.B
    
    // build Wallace tree
    // 17 68 bits data, fill all CSA3s, last for 7 periods
    // 18 / 3 = 6 CSAs -> 18 boothP -> 12 boothP
    // 12 / 3 = 4 CSAs -> 12 boothP -> 8 boothP
    // 8 / 3 = 2 CSAs + 2 datas 
    // 6 / 3 = 2 CSAs
    // 4 / 3 = 1 CSA + 1 data
    // 3 / 3 = 1 CSA + 2 datas
    // do 68 bits add

    // 5 -> 4 -> 2 -> 2 -> 1 -> 1
    // cycleCounter == 9, results done
    cycleCounter := Mux(io.multReady, 0.U, cycleCounter + io.mult_input_info.valid.asUInt)
    
    val CSA3Adders = Seq.fill(adderNum)(
        Module(new CSA3((p(DATA_WIDTH) + 2) * 2))
    )

    for(i <- 0 until adderNum) {
        CSA3Adders(i).io.datas(0) := boothP(3 * i)
        CSA3Adders(i).io.datas(1) := boothP(3 * i + 1)
        CSA3Adders(i).io.datas(2) := boothP(3 * i + 2)
    }

    // csa -> booth mapping
    for(i <- 0 until boothNum) {
        // cycle2: fill boothPs using cycle1's pp and c
        when(cycleCounter === 2.U) {
            if(i < boothNum - 1) {
                boothP(i) := RegNext(booth2BitModules(i).io.pp)
            }
            else {
                boothP(i) := PopCount(
                    Cat((0 until boothNum - 1).map(j =>
                        RegNext(booth2BitModules(j).io.c(0))
                    ))
                )
            }
        }.otherwise {
            if(i / 2 < adderNum) {
                if(i % 2 == 0) {
                    boothP(i) := CSA3Adders(i / 2).io.sum
                }
                else {
                    boothP(i) := CSA3Adders(i / 2).io.carry
                }
            }
            else {
                boothP(i) := 0.U
            }
        }
    }

    val result = WireInit(0.U(((p(DATA_WIDTH) + 2) * 2).W))

    result := boothP(0) + boothP(1)

    val hi = WireInit(0.U(p(DATA_WIDTH).W))
    val lo = WireInit(0.U(p(DATA_WIDTH).W))

    hi := result(64, 33)
    lo := result(32, 1)

    io.result := Mux(io.mult_input_info.funct3 === 0.U, lo, hi)

    io.multReady := cycleCounter === 9.U
}

// to do: pipelined version
