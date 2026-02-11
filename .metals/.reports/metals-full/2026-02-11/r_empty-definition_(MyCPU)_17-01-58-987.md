file://<WORKSPACE>/MyCPU/src/main/scala/MyCPU/src/config/CPUConfig.scala
empty definition using pc, found symbol in pc: 
semanticdb not found
empty definition using fallback
non-local guesses:
	 -org/chipsalliance/cde/config/FETCH_WIDTH.
	 -org/chipsalliance/cde/config/FETCH_WIDTH#
	 -org/chipsalliance/cde/config/FETCH_WIDTH().
	 -FETCH_WIDTH.
	 -FETCH_WIDTH#
	 -FETCH_WIDTH().
	 -scala/Predef.FETCH_WIDTH.
	 -scala/Predef.FETCH_WIDTH#
	 -scala/Predef.FETCH_WIDTH().
offset: 191
uri: file://<WORKSPACE>/MyCPU/src/main/scala/MyCPU/src/config/CPUConfig.scala
text:
```scala
package MyCPU.config
import org.chipsalliance.cde.config._


class CPUConfig extends Config(
    (site, here, up) => {
        case FETCH_WIDTH => 4
        case 
    }
){
    val FETCH_WIDTH@@ = 4;

    val DATA_WIDTH = 32;
    val ADDR_WIDTH = 32;

    val AREG_WIDTH = 5;
    val PREG_WIDTH = AREG_WIDTH + 2;
    val ROBID_WIDTH = AREG_WIDTH + 2;
    
    
}
```


#### Short summary: 

empty definition using pc, found symbol in pc: 