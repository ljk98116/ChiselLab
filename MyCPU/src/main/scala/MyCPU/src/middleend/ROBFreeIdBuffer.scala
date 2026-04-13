package MyCPU.middleend

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._

import org.chipsalliance.cde.config._
import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._
import chisel3.experimental.dataview.DataViewable

class ROBFreeIdBuffer(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        // rename1 io
        val rename1ReadReq = Input(Vec(p(FETCH_WIDTH), new ROBFreeIdAllocReq))
        val rename1ReadResp = Output(Vec(p(FETCH_WIDTH), new ROBFreeIdAllocResp))
        val canEnq = Output(Bool())
        val canDeq = Output(Bool())
        // commit io
        val flush = Input(Bool())
        val commitWriteBack = Input(Vec(p(FETCH_WIDTH), new ROBFreeIdWriteReq))
    })

    val totalSize = 1 << p(ROBID_WIDTH)
    val perBankSize = totalSize / p(FETCH_WIDTH)
    val ptrWidth = log2Ceil(perBankSize)

    val buffers = RegInit(VecInit(
        Seq.tabulate(p(FETCH_WIDTH))(
            b => VecInit(Seq.tabulate(perBankSize)(
                i => (b * perBankSize + i).U
            ))
        )
    ))

    val heads = Seq.fill(p(FETCH_WIDTH))(
        RegInit(0.U(ptrWidth.W))
    )

    val tails = Seq.fill(p(FETCH_WIDTH))(
        RegInit((perBankSize - 1).U(ptrWidth.W))
    )

    io.canDeq := VecInit((0 until p(FETCH_WIDTH)).map(i => 
        heads(i) =/= tails(i)
    )).asUInt.andR

    io.canEnq := VecInit((0 until p(FETCH_WIDTH)).map(i => 
        tails(i) + 1.U =/= heads(i)    
    )).asUInt.andR

    for(i <- 0 until p(FETCH_WIDTH)) {
        when(io.canEnq && io.commitWriteBack(i).valid) {
            buffers(i)(tails(i)) := io.commitWriteBack(i).robId
        }
        io.rename1ReadResp(i).valid := io.rename1ReadReq(i).valid && io.canDeq
        io.rename1ReadResp(i).robId := buffers(i)(heads(i))

        heads(i) := heads(i) + (io.rename1ReadReq(i).valid && io.canDeq)
        tails(i) := tails(i) + (io.commitWriteBack(i).valid && io.canEnq)
    }

    when(io.flush || reset.asBool) {
        for(i <- 0 until p(FETCH_WIDTH)) {
            for(j <- 0 until perBankSize) {
                buffers(i)(j) := (i * perBankSize + j).U
            }
            heads(i) := 0.U
            tails(i) := (perBankSize - 1).U
        }
    }

}