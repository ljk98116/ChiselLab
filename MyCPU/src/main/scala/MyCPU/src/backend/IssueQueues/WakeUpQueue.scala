package MyCPU.backend

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

class WakeUpQueue(implicit p: Parameters) extends Module {
  val bankNum = WakeUpQueuePortNum(p)
  val io = IO(new Bundle {
    val flush = Input(Bool())
    val alu_iq_outputs = Input(Vec(p(ALU_NUM), new ALUIQEntry))
    val mult_iq_outputs = Input(Vec(p(MULT_NUM), new ALUIQEntry))
    val sys_iq_outputs = Input(Vec(p(SYS_ALU_NUM), new SYSIQEntry))
    val wakeup_signals = Output(Vec(bankNum, new WakeUpQueueEntry))
  })

  val enqItems = WireInit(VecInit(
    Seq.fill(bankNum)(0.U.asTypeOf(new WakeUpQueueEntry))
  ))

  for(i <- 0 until bankNum) {
    var subValue = 0
    if(i >= p(ALU_NUM) && i < p(ALU_NUM) + p(MULT_NUM)) {
      subValue = p(ALU_NUM)
    }
    else if(i >= p(ALU_NUM) + p(MULT_NUM) && i < p(ALU_NUM) + p(MULT_NUM) + p(SYS_ALU_NUM)) {
      subValue = p(ALU_NUM) + p(MULT_NUM)
    }

    if(i < p(ALU_NUM)) {
      enqItems(i).valid := io.alu_iq_outputs(i - subValue).valid
      enqItems(i).HasRd := io.alu_iq_outputs(i - subValue).HasRd
      enqItems(i).pd := io.alu_iq_outputs(i - subValue).pd
    }
    else if(i < p(ALU_NUM) + p(MULT_NUM)) {
      enqItems(i).valid := io.mult_iq_outputs(i - subValue).valid
      enqItems(i).HasRd := io.mult_iq_outputs(i - subValue).HasRd
      enqItems(i).pd := io.mult_iq_outputs(i - subValue).pd
    }
    else {
      enqItems(i).valid := io.sys_iq_outputs(i - subValue).valid
      enqItems(i).HasRd := io.sys_iq_outputs(i - subValue).HasRd
      enqItems(i).pd := io.sys_iq_outputs(i - subValue).pd
    }
  }

  def buildPipeline(lat: Int, signal: WakeUpQueueEntry, flush: Bool): WakeUpQueueEntry = {
    if(lat == 0) return signal
    return buildPipeline(lat - 1, PipeReg(signal, false.B, flush), flush)
  }

  val latList = Seq.tabulate(bankNum)(idx => {
    if(idx < p(ALU_NUM) || idx >= p(ALU_NUM) + p(MULT_NUM)) 0
    9
  })

  val wakeUpQueues = Seq.tabulate(bankNum)(idx => buildPipeline(latList(idx), enqItems(idx), io.flush))

  io.wakeup_signals := VecInit(wakeUpQueues)
}