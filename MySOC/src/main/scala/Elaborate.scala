import MyCPU.utils._
import MyCPU._
import MyCPU.frontend._
import MyCPU.middleend._
import MyCPU.backend._

import chisel3._
import org.chipsalliance.cde.config._
import MyCPU.config._
import MyCPU.cache._

import chisel3.stage.ChiselGeneratorAnnotation
import circt.stage.FirtoolOption

object Elaborate extends App {
    val firtoolOptions = Array(
        "--lowering-options=" + List(
            // make yosys happy
            // see https://github.com/llvm/circt/blob/main/docs/VerilogGeneration.md
            "disallowLocalVariables",
            "disallowPackedArrays",
            "locationInfoStyle=wrapInAtSquareBracket"
        ).reduce(_ + "," + _)
    )
    implicit val p: Parameters = 
        new RV64BTBConfig ++ new BaseRV64IConfig ++ 
        new GSharePredictorConfig ++ new L1CacheConfig ++
        new UseSv32Config ++
        new UseCustomLibraryConfig ++
        new StoreSetConfig

    // circt.stage.ChiselStage.emitSystemVerilogFile(
    //     new DivUnit, 
    //     args,
    //     firtoolOptions
    // )
    circt.stage.ChiselStage.emitSystemVerilogFile(
        new BackEnd,
        Array("--output-annotation-file", "/home/ljk/Arch/ChiselLab/MyCPUAnno.json") ++ args,
        firtoolOptions
    )
}