file://<WORKSPACE>/MyCPU/src/main/scala/MyCPU/src/io/PRFIO.scala
empty definition using pc, found symbol in pc: 
semanticdb not found
empty definition using fallback
non-local guesses:
	 -chisel3/PREG_WIDTH.
	 -chisel3/PREG_WIDTH#
	 -chisel3/PREG_WIDTH().
	 -chisel3/util/PREG_WIDTH.
	 -chisel3/util/PREG_WIDTH#
	 -chisel3/util/PREG_WIDTH().
	 -org/chipsalliance/cde/config/PREG_WIDTH.
	 -org/chipsalliance/cde/config/PREG_WIDTH#
	 -org/chipsalliance/cde/config/PREG_WIDTH().
	 -MyCPU.config.PREG_WIDTH.
	 -MyCPU.config.PREG_WIDTH#
	 -MyCPU.config.PREG_WIDTH().
	 -PREG_WIDTH.
	 -PREG_WIDTH#
	 -PREG_WIDTH().
	 -scala/Predef.PREG_WIDTH.
	 -scala/Predef.PREG_WIDTH#
	 -scala/Predef.PREG_WIDTH().
offset: 225
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
    val addr = UInt(p(PREG_W@@IDTH).W);
}

class PRFReadRes extends Bundle {
    val data = UInt(p(DATA_WIDTH).W);
}

class PRFWriteReq extends Bundle {
    val en = Bool();
    val addr = UInt(p(PREG_WIDTH).W);
    val mask = UInt(p(DATA_WIDTH).W);
    val data = UInt(p(DATA_WIDTH).W);
}
```


#### Short summary: 

empty definition using pc, found symbol in pc: 