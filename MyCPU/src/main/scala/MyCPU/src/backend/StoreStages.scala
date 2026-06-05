package MyCPU.backend

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

// mem0: send stu aguRes to storeBuffer / access dcache / send aguRes to InorderLoadQueue judge load harvard, write to 
class StoreStages(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val stuAguResult = Input(Vec(p(STU_NUM), new AGUResult))
        
    })
}