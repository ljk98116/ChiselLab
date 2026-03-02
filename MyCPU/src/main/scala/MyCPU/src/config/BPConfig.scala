package MyCPU.config

import org.chipsalliance.cde.config._

case class BPUParams(
    BPUPredictorNum: Int,
    BPUPredictorIndexBits: Int,
    BPUPredictorGlobalHistoryBits: Int,
    BPUPredictorLocalHistoryBits: Int,
    BPUPredictorCounterBits: Int,
    BPUPredictorBankNum: Int
)

case class RASParams(
    RASSize: Int
)

trait BPUConfigKey extends Field[Boolean]
trait BPUParamsKey extends Field[BPUParams]
trait RASParamsKey extends Field[RASParams]

object RASParam extends RASParamsKey
object HasRASKey extends Field[Boolean]

object PredictorName extends Field[String]

object UseGShare extends BPUConfigKey
object UseNullPredictor extends BPUConfigKey
object UseTournament extends BPUConfigKey
object UseTAGE extends BPUConfigKey

object GShareParamKey extends BPUParamsKey
object NullPredictorParamKey extends BPUParamsKey
object TournamentParamKey extends BPUParamsKey
object TAGEParamKey extends BPUParamsKey

class BasePredictorConfig extends Config((site, here, up) => {
    case PredictorName => {
        if (site(UseGShare)) {
            "GSharePredictor"
        } else if (site(UseTournament)) {
            "TournamentPredictor"
        } else if (site(UseTAGE)) {
            "TAGEPredictor"
        } else if (site(UseNullPredictor)) {
            "NullPredictor"
        } else {
            "NullPredictor" // default predictor
        }
    }
})

class RASConfig extends Config(
    (site, here, up) => {
        case HasRASKey => true
        case RASParam => RASParams // default RAS size
    }
)

class PredictorParameters extends Config((site, here, up) => {
    case GShareParamKey => GSharePredictorParams
    case NullPredictorParamKey => NullPredictorParams
    case TournamentParamKey => TournamentPredictorParams
    case TAGEParamKey => TAGEPredictorParams
})

object NullPredictorParams extends BPUParams(
    BPUPredictorNum = 0,
    BPUPredictorIndexBits = 0,
    BPUPredictorGlobalHistoryBits = 0,
    BPUPredictorLocalHistoryBits = 0,
    BPUPredictorCounterBits = 0,
    BPUPredictorBankNum = 0
)

object GSharePredictorParams extends BPUParams(
    BPUPredictorNum = 1,
    BPUPredictorIndexBits = 10, // 1024 entries
    BPUPredictorGlobalHistoryBits = 10, // 10 bits global history
    BPUPredictorLocalHistoryBits = 0,
    BPUPredictorCounterBits = 2, // 2 bits counter
    BPUPredictorBankNum = 4 // equal with fetch_width
)

object TournamentPredictorParams extends BPUParams(
    BPUPredictorNum = 2,
    BPUPredictorIndexBits = 10, // 1024 entries for each predictor
    BPUPredictorGlobalHistoryBits = 10, // 10 bits global history for global predictor
    BPUPredictorLocalHistoryBits = 10, // 10 bits local history for local predictor
    BPUPredictorCounterBits = 2, // 2 bits counter for each predictor
    BPUPredictorBankNum = 4
)

object TAGEPredictorParams extends BPUParams(
    BPUPredictorNum = 4, // 4 predictor tables: base predictor + 3 tagged tables
    BPUPredictorIndexBits = 10, // 1024 entries for each table
    BPUPredictorGlobalHistoryBits = 20, // 20 bits global history
    BPUPredictorLocalHistoryBits = 0,
    BPUPredictorCounterBits = 3, // 3 bits counter for each table
    BPUPredictorBankNum = 4
)

object RASParams extends RASParams(
    RASSize = 16
)

class NullPredictorConfig extends Config(new PredictorParameters ++ new BasePredictorConfig)

class GSharePredictorConfig extends Config(new PredictorParameters ++ new BasePredictorConfig ++ new RASConfig ++ new Config((site, here, up) => {
    case UseGShare => true
}))

class TournamentPredictorConfig extends Config(new PredictorParameters ++ new BasePredictorConfig ++ new RASConfig ++ new Config((site, here, up) => {
    case UseTournament => true
}))

class TAGEPredictorConfig extends Config(new PredictorParameters ++ new BasePredictorConfig ++ new RASConfig ++ new Config((site, here, up) => {
    case UseTAGE => true
}))