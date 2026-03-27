package MyCPU.config

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._

import org.chipsalliance.cde.config._

import MyCPU.io._

case class BTBParams(
    btbEntryNum: Int,
    btbWayNum: Int = 1,
    btbIndexBits: Int,
    btbTagBits: Int,
    btbOffsetBits: Int,
    btbBankNum: Int = 2
)

// L1 BTB is 2-way or 4-way set associative, use offset bits
// pc[7, 2] -> index
// pc[2] -> bankid
// pc[7, 3] -> rowid => 32 lines per bank x 2 way <=>  64 items perbank

// pc[4, 2] -> index
// pc[3, 2] => bankid
// pc[4] -> rowid
// 2 line per bank x 2way <=> 4 items perbank

object TwoWayBTBParams extends BTBParams(
    btbEntryNum = 16,
    btbWayNum = 2,
    btbIndexBits = 3,
    btbTagBits = 2,
    btbOffsetBits = 2,
    btbBankNum = 4
)

object DirectMapBTBParams extends BTBParams (
    btbEntryNum = 64,
    btbWayNum = 1,
    btbIndexBits = 6,
    btbTagBits = 15,
    btbOffsetBits = 2,
    btbBankNum = 4    
)

// L1 BTB is 2-way or 4-way set associative, use offset bits
// pc[7, 2] -> index
// pc[3, 2] -> bankid
// pc[7, 4] -> rowid => 16 lines per bank x 4 way <=> 64 items perbank

// pc[4, 2] -> index
// pc[3, 2] => bankid
// pc[4] -> rowid
// 2 line per bank x 4way <=> 8 items perbank

// pc[5, 2]
// pc[5, 4] -> rowid => 4 lines perbank x 4way = 16items perbank 
object FourWayBTBParams extends BTBParams(
    btbEntryNum = 64,
    btbWayNum = 4,
    btbIndexBits = 4,
    btbTagBits = 14,
    btbOffsetBits = 2,
    btbBankNum = 4
)

object L0BTBParams extends BTBParams(
    btbEntryNum = 2,
    btbIndexBits = 1,
    btbTagBits = 14,
    btbOffsetBits = 2,
    btbBankNum = 1
)

trait BTBConfigKey extends Field[Boolean]
trait BTBParamsKey extends Field[BTBParams]

object UseTwoWayBTB extends BTBConfigKey
object UseFourWayBTB extends BTBConfigKey  
object UseDirectMapBTB extends BTBConfigKey

object L0BTBParam extends BTBParamsKey
object L1BTBParam extends BTBParamsKey

class TwoWayL1BTBConfig extends Config((site, here, up) => {
    case UseTwoWayBTB => true
    case UseFourWayBTB => false
    case UseDirectMapBTB => false
    case L1BTBParam => TwoWayBTBParams
})

class FourWayL1BTBConfig extends Config((site, here, up) => {
    case UseTwoWayBTB => false
    case UseFourWayBTB => true
    case UseDirectMapBTB => false
    case L1BTBParam => FourWayBTBParams
})

class DirectMapL1BTBConfig extends Config((site, here, up) => {
    case UseTwoWayBTB => false
    case UseFourWayBTB => false
    case UseDirectMapBTB => true
    case L1BTBParam => DirectMapBTBParams
})

class L0BTBConfig extends Config((site, here, up) => {
    case UseTwoWayBTB => false
    case UseFourWayBTB => false
    case L0BTBParam => L0BTBParams
})

class BTBConfig extends Config(new L0BTBConfig ++ new FourWayL1BTBConfig)
