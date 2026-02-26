package MyCPU.io

import chisel3._
import chisel3.util._

import MyCPU.config._
import org.chipsalliance.cde.config._

class PredictorReqInfo(implicit p: Parameters) extends Bundle {
    val pc = UInt(p(ADDR_WIDTH).W)
    val valid = Bool()
}

class PredictorRespInfo(implicit p: Parameters) extends Bundle {
    val valid = Bool()
    val taken = Bool()
    if(p(UseGShare)) {
        val ghist = UInt(p(GShareParamKey).BPUPredictorGlobalHistoryBits.W)
    }
    if(p(UseTournament)) {
        val lhist = UInt(p(TournamentParamKey).BPUPredictorLocalHistoryBits.W)
        val ghist = UInt(p(TournamentParamKey).BPUPredictorGlobalHistoryBits.W)
    }
    if(p(UseTAGE)) {
        val lhist = UInt(p(TAGEParamKey).BPUPredictorLocalHistoryBits.W)
        val ghist = UInt(p(TAGEParamKey).BPUPredictorGlobalHistoryBits.W)
    }
}

class PredictorUpdateInfo(implicit p: Parameters) extends Bundle {
    val valid = Bool()
    val pc = UInt(p(ADDR_WIDTH).W)
    val taken = Bool()
    val misPred = Bool()
    var ghist = UInt(p(NullPredictorParamKey).BPUPredictorGlobalHistoryBits.W)
    var lhist = UInt(p(NullPredictorParamKey).BPUPredictorLocalHistoryBits.W)

    if(p(UseGShare)) {
        ghist = UInt(p(GShareParamKey).BPUPredictorGlobalHistoryBits.W)
    }
    else if(p(UseTournament)) {
        lhist = UInt(p(TournamentParamKey).BPUPredictorLocalHistoryBits.W)
        ghist = UInt(p(TournamentParamKey).BPUPredictorGlobalHistoryBits.W)
    }
    else if(p(UseTAGE)) {
        lhist = UInt(p(TAGEParamKey).BPUPredictorLocalHistoryBits.W)
        ghist = UInt(p(TAGEParamKey).BPUPredictorGlobalHistoryBits.W)
    }
    else {
        ghist = UInt(p(NullPredictorParamKey).BPUPredictorGlobalHistoryBits.W)
        lhist = UInt(p(NullPredictorParamKey).BPUPredictorLocalHistoryBits.W)
    }
}

class BPUReqInfo(implicit p: Parameters) extends Bundle {
    val pc = UInt(p(ADDR_WIDTH).W)
}

class BPUPredictInfo(implicit p: Parameters) extends Bundle {
    val pc = UInt(p(ADDR_WIDTH).W)
    val valid = Bool()
    val taken = Bool()
    val target = UInt(p(ADDR_WIDTH).W)
}

class BPUUpdateInfo(implicit p: Parameters) extends Bundle {
    val pc = UInt(p(ADDR_WIDTH).W)
    val valid = Bool()
    val taken = Bool()
    val target = UInt(p(ADDR_WIDTH).W)
    val misPred = Bool()
}

