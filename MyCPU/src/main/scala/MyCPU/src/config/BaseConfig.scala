package MyCPU.config

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

class WidthConfig extends Field[Int]
class FlagConfig extends Field[Boolean]

case object FETCH_WIDTH extends WidthConfig
case object INST_WIDTH extends WidthConfig
case object DATA_WIDTH extends WidthConfig
case object ADDR_WIDTH extends WidthConfig
case object AREG_WIDTH extends WidthConfig
case object PREG_WIDTH extends WidthConfig
case object ROBID_WIDTH extends WidthConfig
case object FETCH_OFFSET extends WidthConfig

case object SIM_FLAG extends FlagConfig

case object ALU_NUM extends WidthConfig
case object MULT_NUM extends WidthConfig
case object DIV_NUM extends WidthConfig

case object LDU_NUM extends WidthConfig
case object STU_NUM extends WidthConfig
case object SYS_ALU_NUM extends WidthConfig

case object ALUIQ_SIZE extends WidthConfig
case object LDUIQ_SIZE extends WidthConfig
case object STUIQ_SIZE extends WidthConfig
case object SYSIQ_SIZE extends WidthConfig

case object UseRV32E extends FlagConfig
case object UseRV32I extends FlagConfig
case object UseRV32IM extends FlagConfig
case object UseRV64I extends FlagConfig

case object UseTLB extends FlagConfig

class BaseConfig extends Config(
    (site, here, up) => {
        case FETCH_WIDTH => 4
        case INST_WIDTH => 32
        // case DATA_WIDTH => 32
        // case ADDR_WIDTH => 32
        // case AREG_WIDTH => 5
        // case PREG_WIDTH => here(AREG_WIDTH) + 2
        // case ROBID_WIDTH => here(AREG_WIDTH) + 2
        case FETCH_OFFSET => log2Ceil(here(INST_WIDTH) / 8)
        // 8 ports for Integer PRF
        case ALU_NUM => 4
        case MULT_NUM => 1
        case DIV_NUM => 1
        case LDU_NUM => 2
        case STU_NUM => 2
        case SYS_ALU_NUM => 1
        case ALUIQ_SIZE => 8
        case LDUIQ_SIZE => 8
        case STUIQ_SIZE => 8
        case SYSIQ_SIZE => 4
        case SIM_FLAG => true
        case UseTLB => false
    }
)

class BaseRV32EConfig extends Config(
    new BaseConfig ++ new Config(
        (site, here, up) => {
            case DATA_WIDTH => 32
            case ADDR_WIDTH => 32
            case AREG_WIDTH => 4
            case PREG_WIDTH => here(AREG_WIDTH) + 2
            case ROBID_WIDTH => here(AREG_WIDTH) + 2            
            case UseRV32E => true
            case UseRV32I => false
            case UseRV32IM => false
            case UseRV64I => false
        }
    )
)

class BaseRV32IConfig extends Config(
    new BaseConfig ++ new Config(
        (site, here, up) => {
            case DATA_WIDTH => 32
            case ADDR_WIDTH => 32
            case AREG_WIDTH => 5
            case PREG_WIDTH => here(AREG_WIDTH) + 2
            case ROBID_WIDTH => here(AREG_WIDTH) + 2            
            case UseRV32E => false
            case UseRV32I => true
            case UseRV32IM => false
            case UseRV64I => false
        }
    )
)

class BaseRV32IMConfig extends Config(
    new BaseConfig ++ new Config(
        (site, here, up) => {
            case DATA_WIDTH => 32
            case ADDR_WIDTH => 32
            case AREG_WIDTH => 5
            case PREG_WIDTH => here(AREG_WIDTH) + 2
            case ROBID_WIDTH => here(AREG_WIDTH) + 2            
            case UseRV32E => false
            case UseRV32I => false
            case UseRV32IM => true
            case UseRV64I => false
        }
    )
)

class BaseRV64IConfig extends Config(
    new BaseConfig ++ new Config(
        (site, here, up) => {
            case DATA_WIDTH => 64
            case ADDR_WIDTH => 64
            case AREG_WIDTH => 5
            case PREG_WIDTH => here(AREG_WIDTH) + 2
            case ROBID_WIDTH => here(AREG_WIDTH) + 2            
            case UseRV32E => false
            case UseRV32I => false
            case UseRV32IM => false
            case UseRV64I => true
        }
    )
)
