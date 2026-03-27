package MyCPU.config

import org.chipsalliance.cde.config._
import MyCPU.io._
import chisel3._
import chisel3.util._

class WayConfig extends Field[Boolean]
class PolicyConfig extends Field[Boolean]

case object Use4WayL1Cache extends WayConfig
case object Use2WayL1Cache extends WayConfig
case object Use1WayL1Cache extends WayConfig

case object L1CacheOffsetBits extends WidthConfig
case object L1CacheLineLength extends WidthConfig

case object ICacheIndexBits extends WidthConfig
case object DCacheIndexBits extends WidthConfig

case object ICacheTagBits extends WidthConfig
case object DCacheTagBits extends WidthConfig

case object ICacheMSHRNum extends WidthConfig
case object DCacheMSHRNum extends WidthConfig

case object L1CacheWayNum extends WidthConfig

case object UsepLRUPolicy extends PolicyConfig
case object UseRRPolicy extends PolicyConfig
case object UseRandomPolicy extends PolicyConfig

class FourWayL1CacheConfig extends Config(
    (site, here, up) => {
        case Use4WayL1Cache => true
        case Use2WayL1Cache => false
        case Use1WayL1Cache => false
        case L1CacheWayNum => 4
    }
)

class TwoWayL1CacheConfig extends Config(
    (site, here, up) => {
        case Use4WayL1Cache => false
        case Use2WayL1Cache => true
        case Use1WayL1Cache => false
        case L1CacheWayNum => 2
    }
)

class OneWayL1CacheConfig extends Config(
    (site, here, up) => {
        case Use4WayL1Cache => false
        case Use2WayL1Cache => false
        case Use1WayL1Cache => true
        case L1CacheWayNum => 1
    }
)

// 128 x 8x DATA_WIDTH x wayNum
// e.g. 128x8x4Bx4 = 16KB
class L1CacheBaseConfig extends Config(
    (site, here, up) => {
        case L1CacheOffsetBits => 2
        case ICacheIndexBits => 8
        case DCacheIndexBits => 8
        case ICacheTagBits => 
            34 - here(L1CacheOffsetBits) - 
            here(ICacheIndexBits) - site(FETCH_OFFSET) // 34 - 12 = 22
        case L1CacheLineLength => (1 << here(L1CacheOffsetBits))
        case ICacheMSHRNum => 8
        case DCacheMSHRNum => 8
    }
)

class L1CacheConfig extends Config(
    new L1CacheBaseConfig ++ new FourWayL1CacheConfig ++ new ReplacePolicyConfig
)

class CacheRefillReq(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val pa = new PhysicalAddress
}

class UsepLRUPolicyConfig extends Config(
    (site, here, up) => {
        case UseRRPolicy => false
        case UseRandomPolicy => false
        case UsepLRUPolicy => true
    }
)

class UseRRPolicyConfig extends Config(
    (site, here, up) => {
        case UseRRPolicy => true
        case UseRandomPolicy => false
        case UsepLRUPolicy => false
    }    
)

class UseRandomPolicyConfig(implicit p:Parameters) extends Config(
    (site, here, up) => {
        case UseRRPolicy => false
        case UseRandomPolicy => true
        case UsepLRUPolicy => false
    }    
)

class ReplacePolicyConfig extends Config(
    new UsepLRUPolicyConfig
)