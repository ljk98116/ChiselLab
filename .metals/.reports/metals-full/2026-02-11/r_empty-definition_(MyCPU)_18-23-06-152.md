file://<WORKSPACE>/MyCPU/src/main/scala/MyCPU/src/io/PRFIO.scala
empty definition using pc, found symbol in pc: 
semanticdb not found
empty definition using fallback
non-local guesses:
	 -chisel3/PRFWriteReq#
	 -chisel3/util/PRFWriteReq#
	 -org/chipsalliance/cde/config/PRFWriteReq#
	 -MyCPU.config.PRFWriteReq#
	 -PRFWriteReq#
	 -scala/Predef.PRFWriteReq#
offset: 354
uri: file://<WORKSPACE>/MyCPU/src/main/scala/MyCPU/src/io/PRFIO.scala
text:
```scala
package MyCPU.io

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import MyCPU.config._

class PRFReadReq(implicit p: Parameters) extends Bundle {
    val en = Bool();
    val addr = UInt(p(PREG_WIDTH).W);
}

class PRFReadRes(implicit p: Parameters) extends Bundle {
    val data = UInt(p(DATA_WIDTH).W);
}

class PRFWriteReq@@ extends Bundle {
    val en = Bool();
    val addr = UInt(p(PREG_WIDTH).W);
    val mask = UInt(p(DATA_WIDTH).W);
    val data = UInt(p(DATA_WIDTH).W);
}
```


#### Short summary: 

empty definition using pc, found symbol in pc: 