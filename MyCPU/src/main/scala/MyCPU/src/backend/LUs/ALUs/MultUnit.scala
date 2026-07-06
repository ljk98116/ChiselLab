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
class CSA4(width: Int)(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val datas  = Input(Vec(4, UInt(width.W)))
        val sum    = Output(UInt(width.W))
        val carry = Output(UInt(width.W))
    })

    def csa3(a: UInt, b: UInt, c: UInt): (UInt, UInt) = {
        var sum = a ^ b ^ c
        var carry = (a & b) | (a & c) | (b & c)
        (sum, carry << 1.U) 
    }

    val (sum0, carry0) = csa3(io.datas(0), io.datas(1), io.datas(2))
    val (sum1, carry1) = csa3(sum0, carry0, io.datas(3))

    io.sum := sum1
    io.carry := carry1
}

// get P
class BoothMult2Bits(width: Int)(implicit p : Parameters) extends Module {
    val io = IO(new Bundle {
        val en = Input(Bool())
        val x = Input(UInt(width.W))
        val y = Input(Bool())
        val y_add = Input(Bool())
        val y_sub = Input(Bool())
        val p = Output(UInt(width.W))
    })

    val sel_neg = WireInit(false.B)
    val sel_pos = WireInit(false.B)
    val sel_double_neg = WireInit(false.B)
    val sel_double_pos = WireInit(false.B)

    val pp = WireInit(0.U(width.W))

    sel_neg := io.y_add & (io.y & ~io.y_sub | ~io.y & io.y_sub) 
    sel_pos := ~io.y_add & (io.y & ~io.y_sub | ~io.y & io.y_sub)

    sel_double_neg := io.y_add & ~io.y & ~io.y_sub
    sel_double_pos := ~io.y_add & io.y & io.y_sub

    val sel = Cat(sel_neg, sel_pos, sel_double_neg, sel_double_pos)

    pp := Mux1H(
        Seq(
            sel(0) -> Cat(io.x(width - 2, 0), false.B),
            sel(1) -> (~Cat(io.x(width - 2, 0), false.B) + 1.U),
            sel(2) -> io.x,
            sel(3) -> (~io.x + 1.U)
        )
    )

    io.p := Mux(io.en, pp, 0.U)
}

class MultUnit(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val flush = Input(Bool())
        val stall_from_backpipe = Input(Bool())

        // val valid = Input(Bool())
        // val funct3 = Input(UInt(3.W)) 
        // val data1 = Input(UInt(p(DATA_WIDTH).W))
        // val data2 = Input(UInt(p(DATA_WIDTH).W))
        val mult_input_info = Input(new MultUnitInputInfo)

        val result = Output(UInt(p(DATA_WIDTH).W))
        val resValid = Output(Bool()) 

        val multReady = Output(Bool())      
    })

    // decode Funct3
    val sign_ext_d1 = WireInit(false.B)
    val sign_ext_d2 = WireInit(false.B)

    sign_ext_d1 := io.mult_input_info.funct3 =/= Funct3Val.MULHU // MUL/MULH/MULHSU
    sign_ext_d2 := !io.mult_input_info.funct3(1) // MUL/MULH

    // ext d1 and d2
    val sign_ext_data1 = WireInit(0.U(((p(DATA_WIDTH) + 2) * 2).W))
    val sign_ext_data2 = WireInit(0.U((p(DATA_WIDTH) + 2).W))

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

    // build booth 2 bits part mult
    val boothResultVec = RegInit(VecInit(
        Seq.fill((p(DATA_WIDTH) + 2))(0.U(((p(DATA_WIDTH) + 2) * 2).W))
    ))

    // 17 68bits number
    val boothP = RegInit(VecInit(
        Seq.fill((p(DATA_WIDTH) + 2) / 2)(
            0.U(((p(DATA_WIDTH) + 2) * 2).W)
        )
    ))

    // 17 68bits numbers, 17bits c
    // left shift X 2 bits each iter
    // cycle 0 get 17 68bits numbers, 17 bits carry
    val booth2BitModules = Seq.fill((p(DATA_WIDTH) + 2) / 2)(
        Module(new BoothMult2Bits((p(DATA_WIDTH) + 2) * 2))
    )

    for(i <- 0 until(p(DATA_WIDTH) + 2, 2)) {
        val realIdx = i + 1

        booth2BitModules(i / 2).io.en := io.mult_input_info.valid
        booth2BitModules(i / 2).io.x := sign_ext_data1 << i.U
        
        booth2BitModules(i / 2).io.y_sub := sign_ext_data2(realIdx - 1)
        booth2BitModules(i / 2).io.y := sign_ext_data2(realIdx)
        if(realIdx + 1 < p(DATA_WIDTH) + 2) {
            booth2BitModules(i / 2).io.y_add := sign_ext_data2(realIdx + 1)
        }
        else {
            booth2BitModules(i / 2).io.y_add := sign_ext_data2(p(DATA_WIDTH) + 1)
        }
    }

    // build Wallace tree
    // 17 / 4 = 4 CSA 4 adders, 1 data left, 1
    // 8 + 1 / 4 = 2 CSA4 adders, 1 data left, 2
    // 4 + 1 / 4 = 1 CSA4 adders, 1 data left, 3
    // 3 datas -> CSA adder and a full adder, 4
    def csa3(a: UInt, b: UInt, c: UInt): (UInt, UInt) = {
        var sum = a ^ b ^ c
        var carry = (a & b) | (a & c) | (b & c)
        (sum, carry << 1.U) 
    }

    // cycleCounter == 4, results done
    val cycleCounter = RegInit(0.U(3.W))

    cycleCounter := Mux(
        io.multReady, 0.U,
        Mux(io.mult_input_info.valid, cycleCounter + 1.U, cycleCounter)
    )

    // at most 17 68 bits number
    // its a special case, mix CSA4 and CSA3

    val adderNum = (p(DATA_WIDTH) + 2) / 2 / 4
    val boothNum = (p(DATA_WIDTH) + 2) / 2
    val CSA4Adders = Seq.fill(adderNum)(
        Module(new CSA4((p(DATA_WIDTH) + 2) * 2))
    )

    for(i <- 0 until adderNum) {
        when(!io.multReady) {
            if(4 * i < boothNum) {
                CSA4Adders(i).io.datas(0) := boothP(4 * i)
            }
            else {
                CSA4Adders(i).io.datas(0) := 0.U
            }

            if(4 * i + 1 < boothNum) {
                CSA4Adders(i).io.datas(1) := boothP(4 * i + 1)
            }
            else {
                CSA4Adders(i).io.datas(1) := 0.U
            }            

            if(4 * i + 2 < boothNum) {
                CSA4Adders(i).io.datas(2) := boothP(4 * i + 2)
            }
            else {
                CSA4Adders(i).io.datas(2) := 0.U
            }

            if(4 * i + 3 < boothNum) {
                CSA4Adders(i).io.datas(3) := boothP(4 * i + 3)
            }
            else {
                CSA4Adders(i).io.datas(3) := 0.U
            }
        }.otherwise {
            CSA4Adders(i).io.datas(0) := 0.U
            CSA4Adders(i).io.datas(1) := 0.U
            CSA4Adders(i).io.datas(2) := 0.U
            CSA4Adders(i).io.datas(3) := 0.U
        }
    }

    for(i <- 0 until boothNum - 1) {
        when(cycleCounter =/= 0.U) {
            if(i / 2 < adderNum) {
                if(i % 2 == 1) {
                    boothP(i) := CSA4Adders(i / 2).io.carry
                }
                else {
                    boothP(i) := CSA4Adders(i / 2).io.sum
                }
            }
            else {
                boothP(i) := 0.U
            }
        }.otherwise {
            boothP(i) := Mux(io.mult_input_info.valid, booth2BitModules(i).io.p, boothP(i))
        }
    }

    boothP(boothNum - 1) := Mux(
        io.mult_input_info.valid && cycleCounter === 0.U, booth2BitModules(boothNum - 1).io.p, boothP(boothNum - 1)
    )

    val (sum, carry) = csa3(boothP(0), boothP(1), boothP(boothNum - 1))
    val result = WireInit(0.U(((p(DATA_WIDTH) + 2) * 2).W))

    result := sum + carry

    val hi = WireInit(0.U(p(DATA_WIDTH).W))
    val lo = WireInit(0.U(p(DATA_WIDTH).W))

    hi := result(64, 33)
    lo := result(32, 1)

    io.result := Mux(
        io.multReady,
        Mux(io.mult_input_info.funct3 === 0.U, lo, hi),
        0.U
    )

    io.resValid := io.mult_input_info.valid && io.multReady
    io.multReady := (cycleCounter === 4.U)
}