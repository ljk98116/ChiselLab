package MyCPU.config

import org.chipsalliance.cde.config._

case object SSIT_SIZE extends Field[Int]
case object LFST_SIZE extends Field[Int]
case object SLOT_NUM extends Field[Int]

class StoreSetConfig(implicit p: Parameters) extends Config(
    (site, here, up) => {
        case SSIT_SIZE => 64
        case LFST_SIZE => 8
        case SLOT_NUM => 2
    }
)

class MDPConfig(implicit p: Parameters) extends Config(
    new StoreSetConfig
)