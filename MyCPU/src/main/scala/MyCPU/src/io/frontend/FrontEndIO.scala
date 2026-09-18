package MyCPU.io

import chisel3._
import chisel3.util._

import MyCPU.config._
import org.chipsalliance.cde.config._

class IF3RedirectInfo(implicit p: Parameters) extends Bundle {
    val pc = UInt(p(ADDR_WIDTH).W)
    val redirect = Bool()
    val isCall = Bool()
    val isReturn = Bool()
    val target = UInt(p(ADDR_WIDTH).W)
    // used for ras recovery, marked as ras top operated after if2 pred 
    val rasTopPtr = UInt(log2Ceil(p(RASParam).RASSize + 1).W)
}

class BranchPredInfo(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val isCall = Bool()
    val isReturn = Bool()
    val isCond = Bool()
    val target = UInt(p(ADDR_WIDTH).W)
    val counter = UInt(2.W)
    val taken = Bool()

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
    val rasTopPtr = UInt(log2Ceil(p(RASParam).RASSize + 1).W)
}

class InstBufferEntry(implicit p: Parameters) extends Bundle with HasValid {
    val valid = Bool()
    val pc_vec = Vec(p(FETCH_WIDTH), UInt(p(DATA_WIDTH).W))
    val inst_vec = Vec(p(FETCH_WIDTH), UInt(p(INST_WIDTH).W))
    val inst_valid_vec = Vec(p(FETCH_WIDTH), Bool())
    val branch_info = Vec(p(FETCH_WIDTH), new BranchPredInfo)
    val pa = new PhysicalAddress
    val hit = Bool()
    val mshr = UInt(log2Ceil(p(ICacheMSHRNum)).W)
    val mshr_valid = Bool()
    val offsets = Vec(p(FETCH_WIDTH), UInt(p(L1CacheOffsetBits).W))
}