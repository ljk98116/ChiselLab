import MyCPU.utils._
import MyCPU._
import MyCPU.frontend._
import chisel3._
import org.chipsalliance.cde.config._
import MyCPU.config._
import MyCPU.frontend.BPU.L0BTB
import MyCPU.frontend.BPU.L1BTB
import MyCPU.frontend.BPU.L1BTBWbQueue
import MyCPU.frontend.BPU.predictors.GSharePredictor

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
    implicit val p: Parameters = new BTBConfig ++ new BaseConfig ++ new GSharePredictorConfig
    circt.stage.ChiselStage.emitSystemVerilogFile(new GSharePredictor, args, firtoolOptions)
}