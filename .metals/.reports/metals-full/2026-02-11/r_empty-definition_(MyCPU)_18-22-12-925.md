file://<WORKSPACE>/MyCPU/src/main/scala/MyCPU/src/io/PRFIO.scala
empty definition using pc, found symbol in pc: 
semanticdb not found
empty definition using fallback
non-local guesses:
	 -chisel3/Parameters#
	 -chisel3/util/Parameters#
	 -MyCPU.config.Parameters#
	 -Parameters#
	 -scala/Predef.Parameters#
offset: 116
uri: file://<WORKSPACE>/MyCPU/src/main/scala/MyCPU/src/io/PRFIO.scala
text:
```scala
package MyCPU.io

import chisel3._
import chisel3.util._

import MyCPU.config._

class PRFReadReq(implicit p: Parame@@ters) extends Bundle {
    val en = Bool();
    val addr = UInt(CPUConfig.PREG_WIDTH.W);
}

class PRFReadRes extends Bundle {
    val data = UInt(CPUConfig.DATA_WIDTH.W);
}

class PRFWriteReq extends Bundle {
    val en = Bool();
    val addr = UInt(CPUConfig.PREG_WIDTH.W);
    val mask = UInt(CPUConfig.DATA_WIDTH.W);
    val data = UInt(CPUConfig.DATA_WIDTH.W);
}
```


#### Short summary: 

empty definition using pc, found symbol in pc: 