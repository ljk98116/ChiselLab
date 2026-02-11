error id: 910B29436CAE396E3245A4F81A4096AE
file://<WORKSPACE>/MyCPU/src/main/scala/MyCPU/src/config/DecodeConfig.scala
### scala.MatchError: implicit class <error> extends  (of class scala.reflect.internal.Trees$ClassDef)

occurred in the presentation compiler.



action parameters:
offset: 1988
uri: file://<WORKSPACE>/MyCPU/src/main/scala/MyCPU/src/config/DecodeConfig.scala
text:
```scala
package MyCPU.config

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._

import MyCPU.config._
import MyCPU.io._

case class RVRTypeInstPattern(
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
    implicit p@@
    def name: String = "ImmField"
    def chiselType: UInt = UInt(p(DATA_WIDTH).W)
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


presentation compiler configuration:
Scala version: 2.13.18
Classpath:
<WORKSPACE>/out/MyCPU/compiledClassesAndSemanticDbFiles.dest [missing ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/org/scala-lang/scala-library/2.13.18/scala-library-2.13.18.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/org/chipsalliance/chisel_2.13/7.7.0/chisel_2.13-7.7.0.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/com/github/scopt/scopt_2.13/4.1.0/scopt_2.13-4.1.0.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/org/apache/commons/commons-text/1.15.0/commons-text-1.15.0.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/com/lihaoyi/os-lib_2.13/0.10.7/os-lib_2.13-0.10.7.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/io/github/json4s/json4s-native_2.13/4.1.0/json4s-native_2.13-4.1.0.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/io/github/alexarchambault/data-class_2.13/0.2.7/data-class_2.13-0.2.7.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/org/scala-lang/scala-reflect/2.13.18/scala-reflect-2.13.18.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/com/lihaoyi/upickle_2.13/3.3.1/upickle_2.13-3.3.1.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/org/chipsalliance/firtool-resolver_2.13/2.0.1/firtool-resolver_2.13-2.0.1.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/org/apache/commons/commons-lang3/3.20.0/commons-lang3-3.20.0.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/com/lihaoyi/geny_2.13/1.1.1/geny_2.13-1.1.1.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/io/github/json4s/json4s-core_2.13/4.1.0/json4s-core_2.13-4.1.0.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/io/github/json4s/json4s-native-core_2.13/4.1.0/json4s-native-core_2.13-4.1.0.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/com/lihaoyi/ujson_2.13/3.3.1/ujson_2.13-3.3.1.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/com/lihaoyi/upack_2.13/3.3.1/upack_2.13-3.3.1.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/com/lihaoyi/upickle-implicits_2.13/3.3.1/upickle-implicits_2.13-3.3.1.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/org/scala-lang/modules/scala-xml_2.13/2.2.0/scala-xml_2.13-2.2.0.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/org/scala-lang/modules/scala-collection-compat_2.13/2.11.0/scala-collection-compat_2.13-2.11.0.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/io/github/json4s/json4s-ast_2.13/4.1.0/json4s-ast_2.13-4.1.0.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/io/github/json4s/json4s-scalap_2.13/4.1.0/json4s-scalap_2.13-4.1.0.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/com/lihaoyi/upickle-core_2.13/3.3.1/upickle-core_2.13-3.3.1.jar [exists ], <WORKSPACE>/out/cde/compileResources.dest/tools/cde/cde/compile-resources [missing ], <WORKSPACE>/out/cde/compile.dest/classes [exists ], <WORKSPACE>/MyCPU/compile-resources [missing ]
Options:
-language:reflectiveCalls -deprecation -feature -Xcheckinit




#### Error stacktrace:

```
scala.tools.nsc.typechecker.Unapplies.constrParamss(Unapplies.scala:90)
	scala.tools.nsc.typechecker.Unapplies.factoryMeth(Unapplies.scala:155)
	scala.tools.nsc.typechecker.Unapplies.factoryMeth$(Unapplies.scala:152)
	scala.meta.internal.pc.MetalsGlobal$MetalsInteractiveAnalyzer.factoryMeth(MetalsGlobal.scala:85)
	scala.tools.nsc.typechecker.MethodSynthesis$MethodSynth.enterImplicitWrapper(MethodSynthesis.scala:238)
	scala.tools.nsc.typechecker.MethodSynthesis$MethodSynth.enterImplicitWrapper$(MethodSynthesis.scala:237)
	scala.tools.nsc.typechecker.Namers$Namer.enterImplicitWrapper(Namers.scala:58)
	scala.tools.nsc.interactive.InteractiveAnalyzer$InteractiveNamer.enterExistingSym(Global.scala:95)
	scala.tools.nsc.interactive.InteractiveAnalyzer$InteractiveNamer.enterExistingSym$(Global.scala:81)
	scala.tools.nsc.interactive.InteractiveAnalyzer$$anon$2.enterExistingSym(Global.scala:50)
	scala.tools.nsc.typechecker.Namers$Namer.standardEnterSym(Namers.scala:292)
	scala.tools.nsc.typechecker.AnalyzerPlugins.pluginsEnterSym(AnalyzerPlugins.scala:500)
	scala.tools.nsc.typechecker.AnalyzerPlugins.pluginsEnterSym$(AnalyzerPlugins.scala:499)
	scala.meta.internal.pc.MetalsGlobal$MetalsInteractiveAnalyzer.pluginsEnterSym(MetalsGlobal.scala:85)
	scala.tools.nsc.typechecker.Namers$Namer.enterSym(Namers.scala:266)
	scala.tools.nsc.typechecker.Typers$Typer.enterSym(Typers.scala:1995)
	scala.tools.nsc.typechecker.Typers$Typer.enterSyms(Typers.scala:1990)
	scala.tools.nsc.typechecker.Typers$Typer.typedTemplate(Typers.scala:2050)
	scala.tools.nsc.typechecker.Typers$Typer.typedModuleDef(Typers.scala:1950)
	scala.tools.nsc.typechecker.Typers$Typer.typed1(Typers.scala:6268)
	scala.tools.nsc.typechecker.Typers$Typer.typed(Typers.scala:6360)
	scala.tools.nsc.typechecker.Typers$Typer.typedStat$1(Typers.scala:6438)
	scala.tools.nsc.typechecker.Typers$Typer.$anonfun$typedStats$10(Typers.scala:3530)
	scala.tools.nsc.typechecker.Typers$Typer.typedStats(Typers.scala:3530)
	scala.tools.nsc.typechecker.Typers$Typer.typedPackageDef$1(Typers.scala:5934)
	scala.tools.nsc.typechecker.Typers$Typer.typed1(Typers.scala:6270)
	scala.tools.nsc.typechecker.Typers$Typer.typed(Typers.scala:6360)
	scala.tools.nsc.typechecker.Analyzer$typerFactory$TyperPhase.apply(Analyzer.scala:143)
	scala.tools.nsc.Global$GlobalPhase.applyPhase(Global.scala:485)
	scala.tools.nsc.interactive.Global$TyperRun.applyPhase(Global.scala:1371)
	scala.tools.nsc.interactive.Global$TyperRun.typeCheck(Global.scala:1364)
	scala.tools.nsc.interactive.Global.typeCheck(Global.scala:679)
	scala.meta.internal.pc.Compat.$anonfun$runOutline$1(Compat.scala:74)
	scala.collection.IterableOnceOps.foreach(IterableOnce.scala:630)
	scala.collection.IterableOnceOps.foreach$(IterableOnce.scala:628)
	scala.collection.AbstractIterable.foreach(Iterable.scala:936)
	scala.meta.internal.pc.Compat.runOutline(Compat.scala:66)
	scala.meta.internal.pc.Compat.runOutline(Compat.scala:35)
	scala.meta.internal.pc.Compat.runOutline$(Compat.scala:33)
	scala.meta.internal.pc.MetalsGlobal.runOutline(MetalsGlobal.scala:39)
	scala.meta.internal.pc.ScalaCompilerWrapper.compiler(ScalaCompilerAccess.scala:18)
	scala.meta.internal.pc.ScalaCompilerWrapper.compiler(ScalaCompilerAccess.scala:13)
	scala.meta.internal.pc.ScalaPresentationCompiler.$anonfun$complete$1(ScalaPresentationCompiler.scala:236)
	scala.meta.internal.pc.CompilerAccess.retryWithCleanCompiler(CompilerAccess.scala:182)
	scala.meta.internal.pc.CompilerAccess.$anonfun$withSharedCompiler$1(CompilerAccess.scala:155)
	scala.Option.map(Option.scala:242)
	scala.meta.internal.pc.CompilerAccess.withSharedCompiler(CompilerAccess.scala:154)
	scala.meta.internal.pc.CompilerAccess.$anonfun$withInterruptableCompiler$1(CompilerAccess.scala:92)
	scala.meta.internal.pc.CompilerAccess.$anonfun$onCompilerJobQueue$1(CompilerAccess.scala:209)
	scala.meta.internal.pc.CompilerJobQueue$Job.run(CompilerJobQueue.scala:152)
	java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1144)
	java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:642)
	java.base/java.lang.Thread.run(Thread.java:1575)
```
#### Short summary: 

scala.MatchError: implicit class <error> extends  (of class scala.reflect.internal.Trees$ClassDef)