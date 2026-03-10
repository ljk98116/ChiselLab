package MyCPU.io

import chisel3._
import chisel3.util._

import MyCPU.config._
import org.chipsalliance.cde.config._

class IF1RedirectInfo(implicit p: Parameters) extends Bundle {
    val redirect = Bool()
    val isCall = Bool()
    val isReturn = Bool()
    val target = UInt(p(ADDR_WIDTH).W)
    // used for ras recovery, marked as ras top operated after if1 pred 
    val rasTopPtr = UInt(log2Ceil(p(RASParam).RASSize + 1).W)
}

class BranchPredInfo(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val isCall = Bool();
    val isReturn = Bool();
    val target = UInt(p(ADDR_WIDTH).W)
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