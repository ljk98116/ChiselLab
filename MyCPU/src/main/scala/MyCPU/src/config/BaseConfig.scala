package MyCPU.config

import org.chipsalliance.cde.config._

class WidthConfig extends Field[Int]
class FlagConfig extends Field[Boolean]

case object FETCH_WIDTH extends WidthConfig
case object DATA_WIDTH extends WidthConfig
case object ADDR_WIDTH extends WidthConfig
case object AREG_WIDTH extends WidthConfig
case object PREG_WIDTH extends WidthConfig
case object ROBID_WIDTH extends WidthConfig

case object SIM_FLAG extends FlagConfig

class BaseConfig extends Config(
    (site, here, up) => {
        case FETCH_WIDTH => 4
        case DATA_WIDTH => 32
        case ADDR_WIDTH => 32
        case AREG_WIDTH => 5
        case PREG_WIDTH => here(AREG_WIDTH) + 2
        case ROBID_WIDTH => here(AREG_WIDTH) + 2
        case SIM_FLAG => false
    }
)
