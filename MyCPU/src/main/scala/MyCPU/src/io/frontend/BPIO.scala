package MyCPU.io

import chisel3._
import chisel3.util._

import MyCPU.config._
import org.chipsalliance.cde.config._

trait HasValid { this: Bundle =>
    val valid: Bool
}

class PredictorReqInfo(implicit p: Parameters) extends Bundle with HasValid {
    val pc = UInt(p(ADDR_WIDTH).W)
    val valid = Bool()
}

class PredictorRespInfo(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val counter = UInt(2.W)

    val BPUPredictorGlobalHistoryBits = GetBPUPredictorGlobalHistoryBits
    def GetBPUPredictorGlobalHistoryBits: Int = {
        if(p(UseGShare)) return p(GShareParamKey).BPUPredictorGlobalHistoryBits
        else if(p(UseTournament)) return p(TournamentParamKey).BPUPredictorGlobalHistoryBits
        else if(p(UseTAGE)) return p(TAGEParamKey).BPUPredictorGlobalHistoryBits
        return p(NullPredictorParamKey).BPUPredictorGlobalHistoryBits
    }

    val BPUPredictorLocalHistoryBits = GetBPUPredictorLocalHistoryBits
    def GetBPUPredictorLocalHistoryBits: Int = {
        if(p(UseGShare)) return p(GShareParamKey).BPUPredictorLocalHistoryBits
        else if(p(UseTournament)) return p(TournamentParamKey).BPUPredictorLocalHistoryBits
        else if(p(UseTAGE)) return p(TAGEParamKey).BPUPredictorLocalHistoryBits
        return p(NullPredictorParamKey).BPUPredictorLocalHistoryBits
    }

    val ghist = UInt(BPUPredictorGlobalHistoryBits.W)
    val lhist = UInt(BPUPredictorLocalHistoryBits.W)    
}

class PredictorUpdateInfo(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val pc = UInt(p(ADDR_WIDTH).W)
    val counter = UInt(2.W)
    val taken = Bool()
    val misPred = Bool()
    val isCond = Bool()

    val BPUPredictorGlobalHistoryBits = GetBPUPredictorGlobalHistoryBits
    def GetBPUPredictorGlobalHistoryBits: Int = {
        if(p(UseGShare)) return p(GShareParamKey).BPUPredictorGlobalHistoryBits
        else if(p(UseTournament)) return p(TournamentParamKey).BPUPredictorGlobalHistoryBits
        else if(p(UseTAGE)) return p(TAGEParamKey).BPUPredictorGlobalHistoryBits
        return p(NullPredictorParamKey).BPUPredictorGlobalHistoryBits
    }

    val BPUPredictorLocalHistoryBits = GetBPUPredictorLocalHistoryBits
    def GetBPUPredictorLocalHistoryBits: Int = {
        if(p(UseGShare)) return p(GShareParamKey).BPUPredictorLocalHistoryBits
        else if(p(UseTournament)) return p(TournamentParamKey).BPUPredictorLocalHistoryBits
        else if(p(UseTAGE)) return p(TAGEParamKey).BPUPredictorLocalHistoryBits
        return p(NullPredictorParamKey).BPUPredictorLocalHistoryBits
    }

    val ghist = UInt(BPUPredictorGlobalHistoryBits.W)
    val lhist = UInt(BPUPredictorLocalHistoryBits.W)
}

class BPUReqInfo(implicit p: Parameters) extends Bundle with HasValid {
    val pc = UInt(p(ADDR_WIDTH).W)
    val valid = Bool()
}

class BPUPredictInfo(implicit p: Parameters) extends Bundle with HasValid {
    val pc = UInt(p(ADDR_WIDTH).W)
    val valid = Bool()
    val taken = Bool()
    val target = UInt(p(ADDR_WIDTH).W)
}

class BPUUpdateInfo(implicit p: Parameters) extends Bundle with HasValid {
    val pc = UInt(p(ADDR_WIDTH).W)
    val valid = Bool()
    val taken = Bool()
    val target = UInt(p(ADDR_WIDTH).W)
    val misPred = Bool()
}

