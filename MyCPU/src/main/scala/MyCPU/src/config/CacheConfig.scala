package MyCPU.config

import org.chipsalliance.cde.config._

class WayConfig extends Field[Boolean]

case object Use4WayL1Cache extends WayConfig
case object Use2WayL1Cache extends WayConfig
case object Use1WayL1Cache extends WayConfig

case object L1CacheOffsetBits extends WidthConfig
case object L1CacheLineLength extends WidthConfig

case object ICacheIndexBits extends WidthConfig
case object DCacheIndexBits extends WidthConfig

case object ICacheTagBits extends WidthConfig
case object DCacheTagBits extends WidthConfig

case object L1CacheWayNum extends WidthConfig

class FourWayL1CacheConfig(implicit p: Parameters) extends Config(
    (site, here, up) => {
        case Use4WayL1Cache => true
        case Use2WayL1Cache => false
        case Use1WayL1Cache => false
        case L1CacheWayNum => 4
    }
)

class TwoWayL1CacheConfig(implicit p: Parameters) extends Config(
    (site, here, up) => {
        case Use4WayL1Cache => false
        case Use2WayL1Cache => true
        case Use1WayL1Cache => false
        case L1CacheWayNum => 2
    }
)

class OneWayL1CacheConfig(implicit p: Parameters) extends Config(
    (site, here, up) => {
        case Use4WayL1Cache => false
        case Use2WayL1Cache => false
        case Use1WayL1Cache => true
        case L1CacheWayNum => 1
    }
)

// 128 x 8x DATA_WIDTH x wayNum
// e.g. 128x8x4Bx4 = 16KB
class L1CacheBaseConfig(implicit p: Parameters) extends Config(
    (site, here, up) => {
        case L1CacheOffsetBits => 3
        case ICacheIndexBits => 7
        case DCacheIndexBits => 7
        case ICacheTagBits => p(ADDR_WIDTH) - here(L1CacheOffsetBits) - here(ICacheIndexBits) - 2
        case L1CacheLineLength => (1 << here(L1CacheOffsetBits))
    }
)

class L1CacheConfig(implicit p: Parameters) extends Config(
    new L1CacheConfig ++ new FourWayL1CacheConfig
)