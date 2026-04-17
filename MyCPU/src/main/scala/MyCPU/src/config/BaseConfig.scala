package MyCPU.config

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

class WidthConfig extends Field[Int]
class FlagConfig extends Field[Boolean]

case object FETCH_WIDTH extends WidthConfig
case object DATA_WIDTH extends WidthConfig
case object ADDR_WIDTH extends WidthConfig
case object AREG_WIDTH extends WidthConfig
case object PREG_WIDTH extends WidthConfig
case object ROBID_WIDTH extends WidthConfig
case object FETCH_OFFSET extends WidthConfig

case object SIM_FLAG extends FlagConfig

case object ALU_NUM extends WidthConfig
case object AGU_NUM extends WidthConfig
case object SYS_ALU_NUM extends WidthConfig

case object ALUIQ_SIZE extends WidthConfig
case object AGUIQ_SIZE extends WidthConfig

class BaseConfig extends Config(
    (site, here, up) => {
        case FETCH_WIDTH => 4
        case DATA_WIDTH => 32
        case ADDR_WIDTH => 32
        case AREG_WIDTH => 5
        case PREG_WIDTH => here(AREG_WIDTH) + 2
        case ROBID_WIDTH => here(AREG_WIDTH) + 2
        case FETCH_OFFSET => log2Ceil(here(DATA_WIDTH) / 8)
        case ALU_NUM => here(FETCH_WIDTH)
        case AGU_NUM => 2
        case SYS_ALU_NUM => 1
        case ALUIQ_SIZE => 16
        case AGUIQ_SIZE => 
        case SIM_FLAG => false
    }
)
