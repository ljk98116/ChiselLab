file://<WORKSPACE>/MyCPU/src/main/scala/MyCPU/src/io/cache/ICacheIO.scala
empty definition using pc, found symbol in pc: 
semanticdb not found
empty definition using fallback
non-local guesses:
	 -chisel3/ICacheLineEntry#
	 -chisel3/util/ICacheLineEntry#
	 -org/chipsalliance/cde/config/ICacheLineEntry#
	 -MyCPU.config.ICacheLineEntry#
	 -ICacheLineEntry#
	 -scala/Predef.ICacheLineEntry#
offset: 140
uri: file://<WORKSPACE>/MyCPU/src/main/scala/MyCPU/src/io/cache/ICacheIO.scala
text:
```scala
package MyCPU.io

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._

class ICacheLineEntry@@(implicit p: Parameters) extends Bundle {
    val valid = Bool();
    val tag = UInt(CacheConfig.icache_tag_width.W);
    val index = UInt(CacheConfig.icache_line_idx_width.W);
    val wayIndex = UInt(CacheConfig.icache_way_idx_width.W);
    val data = Vec(CacheConfig.icache_line_data_num, UInt(CPUConfig.DATA_WIDTH.W)); 
}

class ICacheReadReq extends Bundle {
    val en = Bool();
    val addr = UInt(CPUConfig.ADDR_WIDTH.W);
}
```


#### Short summary: 

empty definition using pc, found symbol in pc: 