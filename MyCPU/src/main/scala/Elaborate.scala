import MyCPU.utils._
import MyCPU._
import MyCPU.frontend._
import chisel3._
import org.chipsalliance.cde.config._
import MyCPU.config._
import MyCPU.frontend.BPU._
import MyCPU.frontend.BPU.predictors.GSharePredictor
import MyCPU.cache._

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
        new BTBConfig ++ new BaseConfig ++ 
        new GSharePredictorConfig ++ new L1CacheConfig ++
        new UseSv32Config ++
        new UseNangate45LibraryConfig
        
    circt.stage.ChiselStage.emitSystemVerilogFile(new FrontEnd, args, firtoolOptions)
}