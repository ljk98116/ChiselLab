file://<WORKSPACE>/MyCPU/src/main/scala/MyCPU/src/config/DecodeConfig.scala
empty definition using pc, found symbol in pc: 
semanticdb not found
empty definition using fallback
non-local guesses:
	 -chisel3/chiselType.
	 -chisel3/chiselType#
	 -chisel3/chiselType().
	 -chisel3/util/chiselType.
	 -chisel3/util/chiselType#
	 -chisel3/util/chiselType().
	 -chisel3/util/experimental/decode/chiselType.
	 -chisel3/util/experimental/decode/chiselType#
	 -chisel3/util/experimental/decode/chiselType().
	 -MyCPU.config.chiselType.
	 -MyCPU.config.chiselType#
	 -MyCPU.config.chiselType().
	 -MyCPU.io.chiselType.
	 -MyCPU.io.chiselType#
	 -MyCPU.io.chiselType().
	 -org/chipsalliance/cde/config/chiselType.
	 -org/chipsalliance/cde/config/chiselType#
	 -org/chipsalliance/cde/config/chiselType().
	 -chiselType.
	 -chiselType#
	 -chiselType().
	 -scala/Predef.chiselType.
	 -scala/Predef.chiselType#
	 -scala/Predef.chiselType().
offset: 2065
uri: file://<WORKSPACE>/MyCPU/src/main/scala/MyCPU/src/config/DecodeConfig.scala
text:
```scala
package MyCPU.config

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._

import MyCPU.config._
import MyCPU.io._
import org.chipsalliance.cde.config._

case class RVRTypeInstPattern (
    val funct7: BitPat,
    val rs2: BitPat = BitPat.dontCare(5),
    val rs1: BitPat = BitPat.dontCare(5),
    val funct3: BitPat,
    val rd: BitPat = BitPat.dontCare(5),
    val opcode: BitPat
) extends DecodePattern {
    def bitPat: BitPat = funct7 ## rs2 ## rs1 ## funct3 ## rd ## opcode;
}

case class RVITypeInstPattern(
    val imm: BitPat = BitPat.dontCare(32),
    val rs1: BitPat = BitPat.dontCare(5),
    val funct3: BitPat,
    val rd: BitPat = BitPat.dontCare(5),
    val opcode: BitPat
) extends DecodePattern {
    def bitPat: BitPat = imm(11, 0) ## rs1 ## funct3 ## rd ## opcode;
}

case class RVSTypeInstPattern(
    val imm: BitPat = BitPat.dontCare(32),
    val rs2: BitPat = BitPat.dontCare(5),
    val rs1: BitPat = BitPat.dontCare(5),
    val funct3: BitPat,
    val opcode: BitPat
) extends DecodePattern {
    def bitPat: BitPat = imm(11, 5) ## rs2 ## rs1 ## funct3 ## imm(4, 0) ## opcode;
}

case class RVSBTypeInstPattern(
    val imm: BitPat = BitPat.dontCare(32),
    val rs2: BitPat = BitPat.dontCare(5),
    val rs1: BitPat = BitPat.dontCare(5),
    val funct3: BitPat,
    val opcode: BitPat
) extends DecodePattern {
    def bitPat: BitPat = imm(12) ## imm(10, 5) ## rs2 ## rs1 ## funct3 ## imm(4, 1) ## imm(11) ## opcode;
}

case class RVUTypeInstPattern(
    val imm: BitPat = BitPat.dontCare(32),
    val rd: BitPat = BitPat.dontCare(5),
    val opcode: BitPat
) extends DecodePattern {
    def bitPat: BitPat = imm(31, 12) ## rd ## opcode;
}

case class RVUJTypeInstPattern(
    val imm: BitPat = BitPat.dontCare(32),
    val rd: BitPat = BitPat.dontCare(5),
    val opcode: BitPat
) extends DecodePattern {
    def bitPat: BitPat = imm(20) ## imm(10, 1) ## imm(11) ## imm(19, 12) ## rd ## opcode;
}

object ImmField extends DecodeField[DecodePattern, UInt] {
    def name: String = "ImmField"
    def chiselType@@: UInt = 
    def genTable(op: DecodePattern): BitPat = {
        if(op.isInstanceOf[RVITypeInstPattern]) {
            return BitPat.N(20) ## op.asInstanceOf[RVITypeInstPattern].imm(11, 0)
        }
        if(op.isInstanceOf[RVSTypeInstPattern]) {
            return BitPat.N(20) ## op.asInstanceOf[RVSTypeInstPattern].imm(11, 0)
        }
        if(op.isInstanceOf[RVSBTypeInstPattern]) {
            return BitPat.N(19) ## op.asInstanceOf[RVSBTypeInstPattern].imm(12, 1) ## BitPat.N(1)
        }
        if(op.isInstanceOf[RVUTypeInstPattern]) {
            return op.asInstanceOf[RVUTypeInstPattern].imm(31, 12) ## BitPat.N(12)
        }
        if(op.isInstanceOf[RVUJTypeInstPattern]) {
            return BitPat.N(11) ## op.asInstanceOf[RVUJTypeInstPattern].imm(20, 1) ## BitPat.N(1)
        }
        return BitPat.N(p(DATA_WIDTH))
    }
}

object Rs1Field extends DecodeField[DecodePattern, UInt] {
    def name: String = "Rs1Field"
    def chiselType: UInt = UInt(CPUConfig.AREG_WIDTH.W)
    def genTable(op: DecodePattern): BitPat = {
        if(op.isInstanceOf[RVRTypeInstPattern]) {
            return op.asInstanceOf[RVRTypeInstPattern].rs1
        }
        if(op.isInstanceOf[RVITypeInstPattern]) {
            return op.asInstanceOf[RVITypeInstPattern].rs1
        }
        if(op.isInstanceOf[RVSTypeInstPattern]) {
            return op.asInstanceOf[RVSTypeInstPattern].rs1
        }
        if(op.isInstanceOf[RVSBTypeInstPattern]) {
            return op.asInstanceOf[RVSBTypeInstPattern].rs1
        }
        return BitPat.N(CPUConfig.AREG_WIDTH)
    }
}

object Rs2Field extends DecodeField[DecodePattern, UInt] {
    def name: String = "Rs2Field"
    def chiselType: UInt = UInt(CPUConfig.AREG_WIDTH.W)
    def genTable(op: DecodePattern): BitPat = {
        if(op.isInstanceOf[RVRTypeInstPattern]) {
            return op.asInstanceOf[RVRTypeInstPattern].rs2
        }        
        if(op.isInstanceOf[RVSTypeInstPattern]) {
            return op.asInstanceOf[RVSTypeInstPattern].rs2
        }
        if(op.isInstanceOf[RVSBTypeInstPattern]) {
            return op.asInstanceOf[RVSBTypeInstPattern].rs2
        }
        return BitPat.N(CPUConfig.AREG_WIDTH)
    }
}

object RdField extends DecodeField[DecodePattern, UInt] {
    def name: String = "RdField"
    def chiselType: UInt = UInt(CPUConfig.AREG_WIDTH.W)
    def genTable(op: DecodePattern): BitPat = {
        if(op.isInstanceOf[RVRTypeInstPattern]) {
            return op.asInstanceOf[RVRTypeInstPattern].rd
        }        
        if(op.isInstanceOf[RVITypeInstPattern]) {
            return op.asInstanceOf[RVITypeInstPattern].rd
        }
        if(op.isInstanceOf[RVUTypeInstPattern]) {
            return op.asInstanceOf[RVUTypeInstPattern].rd
        }
        if(op.isInstanceOf[RVUJTypeInstPattern]) {
            return op.asInstanceOf[RVUJTypeInstPattern].rd
        }
        return BitPat.N(CPUConfig.AREG_WIDTH)
    }
}

object OpcodeField extends DecodeField[DecodePattern, UInt] {
    def name: String = "OpcodeField"
    def chiselType: UInt = UInt(7.W)
    def genTable(op: DecodePattern): BitPat = {
        if(op.isInstanceOf[RVRTypeInstPattern]) {
            return op.asInstanceOf[RVRTypeInstPattern].opcode
        }        
        if(op.isInstanceOf[RVITypeInstPattern]) {
            return op.asInstanceOf[RVITypeInstPattern].opcode
        }
        if(op.isInstanceOf[RVUTypeInstPattern]) {
            return op.asInstanceOf[RVUTypeInstPattern].opcode
        }
        if(op.isInstanceOf[RVUJTypeInstPattern]) {
            return op.asInstanceOf[RVUJTypeInstPattern].opcode
        }
        if(op.isInstanceOf[RVSTypeInstPattern]) {
            return op.asInstanceOf[RVSTypeInstPattern].opcode
        }
        if(op.isInstanceOf[RVSBTypeInstPattern]) {
            return op.asInstanceOf[RVSBTypeInstPattern].opcode
        }
        return BitPat.N(7)
    }
}

object Funct3Field extends DecodeField[DecodePattern, UInt] {
    def name: String = "Funct3Field"
    def chiselType: UInt = UInt(3.W)
    def genTable(op: DecodePattern): BitPat = {
        if(op.isInstanceOf[RVRTypeInstPattern]) {
            return op.asInstanceOf[RVRTypeInstPattern].funct3
        }        
        if(op.isInstanceOf[RVITypeInstPattern]) {
            return op.asInstanceOf[RVITypeInstPattern].funct3
        }
        if(op.isInstanceOf[RVSTypeInstPattern]) {
            return op.asInstanceOf[RVSTypeInstPattern].funct3
        }
        if(op.isInstanceOf[RVSBTypeInstPattern]) {
            return op.asInstanceOf[RVSBTypeInstPattern].funct3
        }
        return BitPat.N(3)
    }
}

object Funct7Field extends DecodeField[DecodePattern, UInt] {
    def name: String = "Funct7Field"
    def chiselType: UInt = UInt(7.W)
    def genTable(op: DecodePattern): BitPat = {
        if(op.isInstanceOf[RVRTypeInstPattern]) {
            return op.asInstanceOf[RVRTypeInstPattern].funct7
        }        
        return BitPat.N(7)
    }
}

object LatField extends DecodeField[DecodePattern, UInt] {
    def name: String = "LatField"
    def chiselType: UInt = UInt(3.W)
    def genTable(op: DecodePattern): BitPat = {
        // to do
        return  BitPat.N(3)
    }
}

object Opcode {
    val LUI: BitPat     = BitPat("b011 0111")
    val AUIPC: BitPat   = BitPat("b001 0111")

    val JAL: BitPat     = BitPat("b110 1111")

    val JALR: BitPat    = BitPat("b110 0111")

    val BEQ: BitPat     = BitPat("b110 0011")
    val BNE: BitPat     = BEQ
    val BLT: BitPat     = BEQ
    val BGE: BitPat     = BEQ
    val BLTU: BitPat    = BEQ
    val BGEU: BitPat    = BEQ
}

object Funct3 {
    val JALR: BitPat = BitPat("b000")

    val BEQ: BitPat = BitPat("b000")

}
```


#### Short summary: 

empty definition using pc, found symbol in pc: 