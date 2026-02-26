package MyCPU.frontend.BPU.predictors

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._

import org.chipsalliance.cde.config._
import MyCPU.config._
import MyCPU.io._

// gshare predictor is accessed in if1 stage, update when commit
// if0 stage generate pht index and read pht and if1 stage generate prediction result, wb stage update pht & ghr
class GSharePredictor(implicit p: Parameters) extends Predictor {
    val fetchWidth = p(FETCH_WIDTH)
    val indexBits = p(GShareParamKey).BPUPredictorIndexBits
    val globalHistoryBits = p(GShareParamKey).BPUPredictorGlobalHistoryBits
    val counterBits = p(GShareParamKey).BPUPredictorCounterBits

    val predictorSize = 1 << indexBits

    var ghist = RegInit(0.U(globalHistoryBits.W))

    // ghr update
    when (io.update.valid) {
        ghist := Cat(ghist(globalHistoryBits - 2, 0), io.update.taken)
    }

    // predictor table
    // phtIndex: (pc >> 2) ^ ghr, get pht item at if1 stage
    val phtIndex = (io.req.pc >> 2)(indexBits - 1, 0) ^ ghist(globalHistoryBits - 1, globalHistoryBits - indexBits)

    val phtAccess = io.req.valid

    val phtBank = SyncReadMem(predictorSize / fetchWidth, UInt(counterBits.W))

    // read predictor table at if0 stage
    // output is the counter value, which will be used to generate prediction result at if2 stage
    val phtReadData = phtBank.read(phtIndex)

    // generate prediction result at if1 stage
    val counterValue = phtReadData
    io.predict.valid := io.req.valid
    io.predict.taken := counterValue(counterBits - 1) // MSB assume 2-bit counter, taken if MSB is 1

    // update predictor table at wb stage
    val updateIndex = WireInit(0.U(indexBits.W))
    if(p(UseTournament) || p(UseTAGE) || p(UseGShare)) {
        updateIndex := (io.update.pc >> 2)(indexBits - 1, 0) ^ io.update.ghist(globalHistoryBits - 1, globalHistoryBits - indexBits)
    } else {
        updateIndex := (io.update.pc >> 2)(indexBits - 1, 0)
    }

    // update predictor table when update is a valid branch instruction
    when (io.update.valid) {
        val index = updateIndex
        val counterValue = phtBank.read(index)
        when (io.update.taken && !io.update.misPred) {
            // if taken and not mispredicted, increment counter value
            phtBank.write(index, 
                Mux(counterValue === ((1 << counterBits) - 1).U, counterValue, counterValue + 1.U)
            )
        } .elsewhen (!io.update.taken && !io.update.misPred) {
            // if not taken and not mispredicted, decrement counter value
            phtBank.write(index, 
                Mux(counterValue === 0.U, counterValue, counterValue - 1.U)
            )
        } .elsewhen (io.update.misPred) {
            // if mispredicted, update counter value according to actual outcome
            when (io.update.taken) {
                phtBank.write(index, 
                    Mux(counterValue === ((1 << counterBits) - 1).U, counterValue, counterValue + 1.U)
                )
            } .otherwise {
                phtBank.write(index, 
                    Mux(counterValue === 0.U, counterValue, counterValue - 1.U)
                )
            }
        }
    }
}