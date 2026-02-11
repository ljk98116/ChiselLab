file://<WORKSPACE>/MyCPU/src/main/scala/MyCPU/src/config/CPUConfig.scala
empty definition using pc, found symbol in pc: 
semanticdb not found
empty definition using fallback
non-local guesses:
	 -org/chipsalliance/cde/config/ROBID_WIDTH.
	 -org/chipsalliance/cde/config/ROBID_WIDTH#
	 -org/chipsalliance/cde/config/ROBID_WIDTH().
	 -ROBID_WIDTH.
	 -ROBID_WIDTH#
	 -ROBID_WIDTH().
	 -scala/Predef.ROBID_WIDTH.
	 -scala/Predef.ROBID_WIDTH#
	 -scala/Predef.ROBID_WIDTH().
offset: 559
uri: file://<WORKSPACE>/MyCPU/src/main/scala/MyCPU/src/config/CPUConfig.scala
text:
```scala
package MyCPU.config
import org.chipsalliance.cde.config._

case object FETCH_WIDTH extends Field[Int]
case object DATA_WIDTH extends Field[Int]
case object ADDR_WIDTH extends Field[Int]
case object AREG_WIDTH extends Field[Int]
case object PREG_WIDTH extends Field[Int]
case object PREG_WIDTH extends Field[Int]

class CPUConfig extends Config(
    (site, here, up) => {
        case FETCH_WIDTH => 4
        case DATA_WIDTH => 32
        case ADDR_WIDTH => 32
        case AREG_WIDTH => 5
        case PREG_WIDTH => here(AREG_WIDTH) + 2
        case ROBID_W@@IDTH => here(AREG_WIDTH) + 2
    }
)
```


#### Short summary: 

empty definition using pc, found symbol in pc: 