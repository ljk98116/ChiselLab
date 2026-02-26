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
    btbBankNum: Int = 4
)

// L1 BTB is 2-way or 4-way set associative, use offset bits 
object TwoWayBTBParams extends BTBParams(
    btbEntryNum = 64,
    btbWayNum = 2,
    btbIndexBits = 6,
    btbTagBits = 22,
    btbOffsetBits = 2,
    btbBankNum = 4
)

object FourWayBTBParams extends BTBParams(
    btbEntryNum = 128,
    btbWayNum = 4,
    btbIndexBits = 7,
    btbTagBits = 19,
    btbOffsetBits = 2,
    btbBankNum = 4
)

object L0BTBParams extends BTBParams(
    btbEntryNum = 4,
    btbIndexBits = 2,
    btbTagBits = 28,
    btbOffsetBits = 2,
    btbBankNum = 1
)

trait BTBConfigKey extends Field[Boolean]
trait BTBParamsKey extends Field[BTBParams]

object UseTwoWayBTB extends BTBConfigKey
object UseFourWayBTB extends BTBConfigKey  
object L0BTBParam extends BTBParamsKey
object L1BTBParam extends BTBParamsKey

class TwoWayL1BTBConfig extends Config((site, here, up) => {
    case UseTwoWayBTB => true
    case UseFourWayBTB => false
    case L1BTBParam => TwoWayBTBParams
})

class FourWayL1BTBConfig extends Config((site, here, up) => {
    case UseTwoWayBTB => false
    case UseFourWayBTB => true
    case L1BTBParam => FourWayBTBParams
})

class L0BTBConfig extends Config((site, here, up) => {
    case UseTwoWayBTB => false
    case UseFourWayBTB => false
    case L0BTBParam => L0BTBParams
})

class BTBConfig extends Config(new L0BTBConfig ++ new FourWayL1BTBConfig)
