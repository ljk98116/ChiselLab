file://<WORKSPACE>/MyCPU/src/main/scala/MyCPU/src/io/cache/DCacheIO.scala
empty definition using pc, found symbol in pc: 
semanticdb not found
empty definition using fallback
non-local guesses:
	 -chisel3/DCacheWriteReq#
	 -chisel3/util/DCacheWriteReq#
	 -org/chipsalliance/cde/config/DCacheWriteReq#
	 -MyCPU.config.DCacheWriteReq#
	 -DCacheWriteReq#
	 -scala/Predef.DCacheWriteReq#
offset: 602
uri: file://<WORKSPACE>/MyCPU/src/main/scala/MyCPU/src/io/cache/DCacheIO.scala
text:
```scala
package MyCPU.io

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._

class DCacheLineEntry(implicit p: Parameters) extends Bundle {
    val valid = Bool();
    val tag = UInt(CacheConfig.dcache_tag_width.W);
    val index = UInt(CacheConfig.dcache_line_idx_width.W);
    val wayIndex = UInt(CacheConfig.dcache_way_idx_width.W);
    val data = Vec(CacheConfig.dcache_line_data_num, UInt(p(DATA_WIDTH).W)); 
}

class DCacheReadReq(implicit p: Parameters) extends Bundle {
    val en = Bool();
    val addr = UInt(p(ADDR_WIDTH).W);
}

class DCacheWriteReq@@ extends Bundle {
    val en = Bool();
    val addr = UInt(p(ADDR_WIDTH).W);
    val mask = UInt(p(DATA_WIDTH).W);
    val data = UInt(p(DATA_WIDTH).W);
}
```


#### Short summary: 

empty definition using pc, found symbol in pc: 