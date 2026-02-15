import MyCPU.utils._
import MyCPU._
import MyCPU.frontend._
import chisel3._
import org.chipsalliance.cde.config._
import MyCPU.config._

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
    implicit val p: Parameters = new BaseConfig
    circt.stage.ChiselStage.emitSystemVerilogFile(new DecodeStage, args, firtoolOptions)
}