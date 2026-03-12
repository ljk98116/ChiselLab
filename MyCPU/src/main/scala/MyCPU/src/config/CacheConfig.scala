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
        case ICacheTagBits => 
            (new PhysicalAddress).GetWidth - here(L1CacheOffsetBits) - 
            here(ICacheIndexBits) - site(FETCH_OFFSET)
        case L1CacheLineLength => (1 << here(L1CacheOffsetBits))
        case ICacheMSHRNum => 8
        case DCacheMSHRNum => 8
    }
)

class L1CacheConfig(implicit p: Parameters) extends Config(
    new L1CacheConfig ++ new FourWayL1CacheConfig
)

class CacheRefillReq(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val pa = new PhysicalAddress
}

class UsepLRUPolicyConfig(implicit p: Parameters) extends Config(
    (site, here, up) => {
        case UseRRPolicy => false
        case UseRandomPolicy => false
        case UsepLRUPolicy => true
    }
)

class UseRRPolicyConfig(implicit p: Parameters) extends Config(
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

class ReplacePolicyConfig(implicit p: Parameters) extends Config(
    new UsepLRUPolicyConfig
)