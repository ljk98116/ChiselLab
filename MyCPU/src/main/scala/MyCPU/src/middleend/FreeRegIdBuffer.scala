package MyCPU.middleend;
import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._

import org.chipsalliance.cde.config._
import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

// use multi bank update, lutram memory
// need to save all heads
class FreeRegIdBuffer(implicit p: Parameters) extends Module {

    val totalSize =  1 << p(PREG_WIDTH)
    val perBankSize = totalSize / p(FETCH_WIDTH)
    val ptrWidth = log2Ceil(perBankSize)

    val io = IO(new Bundle {
        // from rename2 stage
        val readReq = Input(Vec(p(FETCH_WIDTH), new FreeRegIdBufferReadReq))
        // to rename2 stage
        val readResp = Output(Vec(p(FETCH_WIDTH), new FreeRegIdBufferReadResp))
        val deqReady = Output(Bool())
        // from commit stage
        val writeReq = Input(Vec(p(FETCH_WIDTH), new FreeRegIdBufferWriteReq))
        // from checkPointQueue
        val checkpointInfo = Input(new CheckPointInfo)
        // to checkpointQueue
        val currentHeads = Output(Vec(p(FETCH_WIDTH), new CheckPointInfo))
    })

    val heads = Seq.fill(p(FETCH_WIDTH))(RegInit(0.U(ptrWidth.W)))
    val tails = Seq.fill(p(FETCH_WIDTH))(RegInit((perBankSize - 1).U(ptrWidth.W)))

    val banks = RegInit(VecInit(
        Seq.tabulate(p(FETCH_WIDTH))(
            b => VecInit(Seq.tabulate(perBankSize)(i => ((b << (p(PREG_WIDTH) - p(FETCH_WIDTH))) + i).U(p(PREG_WIDTH).W)))
        )
    ))

    val canDeq = VecInit((0 until p(FETCH_WIDTH)).map(i => {
        heads(i) =/= tails(i)
    }))

    io.deqReady := canDeq.asUInt.andR

    val readDatas = WireInit(VecInit(
        Seq.fill(p(FETCH_WIDTH))(0.U(p(PREG_WIDTH).W))
    ))

    for(i <- 0 until p(FETCH_WIDTH)) {
        // bank read
        readDatas(i) := Mux(io.readReq(i).valid, banks(i)(heads(i)), 0.U)

        // bank write
        when(io.writeReq(i).valid) {
            banks(i)(tails(i)) := io.writeReq(i).preg
        }

        io.readResp(i).valid := canDeq(i) && io.readReq(i).valid
        io.readResp(i).preg := readDatas(i)

        val currentHeads = WireInit(VecInit(
            Seq.fill(p(FETCH_WIDTH))(0.U(ptrWidth.W))
        ))

        // assume regids in front of i are used
        for(j <- 0 until i) {
            currentHeads(j) := heads(j) + (io.readReq(j).valid & canDeq(j))
        }

        for(j <- i until p(FETCH_WIDTH)) {
            currentHeads(j) := heads(j)
        }

        io.currentHeads(i).valid := io.readReq(i).is_checkPoint
        io.currentHeads(i).freeRegIdBufferHeads := currentHeads

        heads(i) := Mux(
            io.checkpointInfo.valid, 
            io.checkpointInfo.freeRegIdBufferHeads(i), 
            heads(i) + (canDeq(i) & io.readReq(i).valid)
        )

        tails(i) := tails(i) + io.writeReq(i).valid
    }
}

