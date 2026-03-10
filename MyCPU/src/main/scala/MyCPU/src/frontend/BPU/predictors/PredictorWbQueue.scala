package MyCPU.frontend.BPU

import chisel3._ 
import chisel3.util._ 
import MyCPU.config._ 
import MyCPU.io._ 
import MyCPU.utils._
import org.chipsalliance.cde.config._

class PredictorWbQueue(size: Int)(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val enq = Input(Vec(p(FETCH_WIDTH), new PredictorUpdateInfo))
        val deq = Output(Vec(p(FETCH_WIDTH), new PredictorUpdateInfo))
        val enqReady = Output(Bool()) // wr_able
        val deqReady = Output(Vec(p(FETCH_WIDTH), Bool())) // maybe useless        
    })

    val bankNum = p(L1BTBParam).btbBankNum
    val infoWidth = (new PredictorUpdateInfo).getWidth
    val _1stBankBits = log2Ceil(bankNum)
    val offsetBits = p(L1BTBParam).btbOffsetBits
    val perBankSize = size / p(FETCH_WIDTH)

    require(p(L1BTBParam).btbBankNum == p(FETCH_WIDTH), "predictor wbqueue bankNum must equal to fetch width")
    require(size % p(FETCH_WIDTH) == 0, "invalid predictor wbqueue size")

    val queues = Seq.fill(bankNum)(
        Module(new MultiPortQueueNW1R(perBankSize, bankNum, new PredictorUpdateInfo))
    )
    // wb stage1
    // enq items' origin index
    val wb1_enqItemIndexArray = WireInit(VecInit(
        Seq.fill(bankNum)(VecInit(
            Seq.fill(bankNum)(0.U(_1stBankBits.W))
        ))
    ))
    val wb1_enqItemValidArray = WireInit(VecInit(
        Seq.fill(bankNum)(VecInit(
            Seq.fill(bankNum)(false.B)
        ))
    ))

    val defaultIndex = (1 << _1stBankBits).U((_1stBankBits + 1).W)
    // bank i's mapping
    val mapping_lut = VecInit(
        Seq.tabulate(1 << bankNum)(
            mask => mask match {
                case 0 => VecInit(Seq.fill(bankNum)(defaultIndex))
                case 1 => VecInit(Seq(0.U((_1stBankBits + 1).W), defaultIndex, defaultIndex, defaultIndex))
                case 2 => VecInit(Seq(1.U((_1stBankBits + 1).W), defaultIndex, defaultIndex, defaultIndex))
                case 3 => VecInit(Seq(0.U((_1stBankBits + 1).W), 1.U((_1stBankBits + 1).W), defaultIndex, defaultIndex))
                case 4 => VecInit(Seq(2.U((_1stBankBits + 1).W), defaultIndex, defaultIndex, defaultIndex))
                case 5 => VecInit(Seq(0.U((_1stBankBits + 1).W), 2.U((_1stBankBits + 1).W), defaultIndex, defaultIndex))
                case 6 => VecInit(Seq(1.U((_1stBankBits + 1).W), 2.U((_1stBankBits + 1).W), defaultIndex, defaultIndex))
                case 7 => VecInit(Seq(0.U((_1stBankBits + 1).W), 1.U((_1stBankBits + 1).W), 2.U((_1stBankBits + 1).W), defaultIndex))
                case 8 => VecInit(Seq(3.U((_1stBankBits + 1).W), defaultIndex, defaultIndex, defaultIndex))
                case 9 => VecInit(Seq(0.U((_1stBankBits + 1).W), 3.U((_1stBankBits + 1).W), defaultIndex, defaultIndex))
                case 10 => VecInit(Seq(1.U((_1stBankBits + 1).W), 3.U((_1stBankBits + 1).W), defaultIndex, defaultIndex))
                case 11 => VecInit(Seq(0.U((_1stBankBits + 1).W), 1.U((_1stBankBits + 1).W), 3.U((_1stBankBits + 1).W), defaultIndex))
                case 12 => VecInit(Seq(2.U((_1stBankBits + 1).W), 3.U((_1stBankBits + 1).W), defaultIndex, defaultIndex))
                case 13 => VecInit(Seq(0.U((_1stBankBits + 1).W), 2.U((_1stBankBits + 1).W), 3.U((_1stBankBits + 1).W), defaultIndex))
                case 14 => VecInit(Seq(1.U((_1stBankBits + 1).W), 2.U((_1stBankBits + 1).W), 3.U((_1stBankBits + 1).W), defaultIndex))
                case 15 => VecInit(Seq(0.U((_1stBankBits + 1).W), 1.U((_1stBankBits + 1).W), 2.U((_1stBankBits + 1).W), 3.U((_1stBankBits + 1).W)))
            }
        )
    )

    val bankIds = VecInit(io.enq.map(_.pc(_1stBankBits + offsetBits - 1, offsetBits)))
    val wb1_firstEnqIndexArray = WireInit(VecInit(
        Seq.fill(bankNum)(0.U(_1stBankBits.W))
    ))
    val wb1_firstEnqIndexValidArray = WireInit(VecInit(
        Seq.fill(bankNum)(false.B)
    ))

    for(i <-0 until bankNum) {
        // queue deqReady, need to write
        val bankMask = Cat((0 until bankNum).reverse.map { j =>
            (bankIds(j) === i.U) && io.enq(j).valid
        })

        val lutOut = mapping_lut(bankMask)
        val only1write = PopCount(bankMask) === 1.U

        wb1_firstEnqIndexArray(i) := PriorityEncoder(bankMask)
        wb1_firstEnqIndexValidArray(i) := bankMask.orR

        wb1_enqItemIndexArray(i) := lutOut.map(_(_1stBankBits - 1, 0))
        
        val queueEmpty = !queues(i).io.deqReady

        val bypass = queueEmpty && only1write

        val firstOH = UIntToOH(wb1_firstEnqIndexArray(i))
        val bypassMask = firstOH & Fill(bankNum, bypass)

        wb1_enqItemValidArray(i) := VecInit(lutOut.map(~_(_1stBankBits)).zipWithIndex.map(
            {
                case (item, index) => {
                    item & ~bypassMask(index)
                }
            }
        ))
        
        io.deq(i) := Mux(
            bypass,
            io.enq(wb1_firstEnqIndexArray(i)),
            queues(i).io.deq
        )
    }

    // wb2 stage, get banked enq item and write to multi port queue
    val wb2_enqItemIndexesArray = RegNext(wb1_enqItemIndexArray)
    val wb2_enqItemValidArray = RegNext(wb1_enqItemValidArray)
    val wb2_enq = RegNext(io.enq)

    for(i <- 0 until bankNum) {
        for(j <- 0 until bankNum) {
            queues(i).io.enq(j) := wb2_enq(wb2_enqItemIndexesArray(i)(j))
        }
        queues(i).io.enqValid := wb2_enqItemValidArray(i)
    }

    io.deqReady := queues.map(_.io.deqReady)
    io.enqReady := Cat(queues.map(_.io.enqReady)).andR      
}