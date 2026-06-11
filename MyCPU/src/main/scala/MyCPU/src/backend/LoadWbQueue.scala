package MyCPU.backend

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._
import MyCPU.cache._

class LoadCacheInfo(implicit p: Parameters) extends Bundle {
    val valid = Bool()
    val hit = Bool()
    val pa = new PhysicalAddress
    val cacheLine = new DCacheLine
    val robLoc = UInt(p(ROBID_WIDTH).W)
    val hitWay = UInt(log2Ceil(p(L1CacheWayNum)).W) 
}

class LoadBaseInfo(implicit p: Parameters) extends Bundle {
    val readPortNum = p(LDU_NUM)
    val writePortNum = p(LDU_NUM)
    val bankNum = writePortNum + readPortNum
    val bankBits = log2Ceil(bankNum)

    val valid = Bool()    
    val mask = UInt(p(DATA_WIDTH).W)
    val rd = UInt(p(AREG_WIDTH).W)
    val pd = UInt(p(PREG_WIDTH).W)
    val HasRd = Bool()
    val is_sign_ext = Bool()
    val bankId = UInt(bankBits.W)
}

class LoadWbQueue(size: Int)(implicit p: Parameters) extends Module {
    val bankNum = p(LDU_NUM)
    val bankBits = log2Ceil(bankNum)
    val ptrWidth = log2Ceil(size)
    val perBankSize = size / bankNum
    val rowPtrWidth = log2Ceil(perBankSize)

    val io = IO(new Bundle {
        val flush = Input(Bool())
        val mem4_ldu_result = Input(Vec(p(LDU_NUM), new LoadWbQueueItem))
        val mem5_ldu_deqs = Output(Vec(p(LDU_NUM), new LoadWbQueueItem))
        val dcache_refill_resp = Input(new DCacheRefillResp)
        val dcache_way_refill_resp = Input(new ReplacePolicyRefillRespInfo)
        val mem5_ldu_wb_stall = Output(Bool())
    })

    val loadBaseInfoBanks = Seq.fill(bankNum)(
        RegInit(VecInit(
            Seq.fill(perBankSize)(0.U.asTypeOf(new LoadBaseInfo))
        ))
    )

    val loadCacheInfoBanks = Seq.fill(bankNum)(
        RegInit(VecInit(
            Seq.fill(perBankSize)(0.U.asTypeOf(new LoadCacheInfo))
        ))
    )

    val headPtr = RegInit(0.U(rowPtrWidth.W))
    val tailPtr = RegInit(0.U(rowPtrWidth.W))

    val canDeq = VecInit((0 until bankNum).map(i => 
        (loadCacheInfoBanks(i)(headPtr).valid && loadCacheInfoBanks(i)(headPtr).hit) || !loadCacheInfoBanks(i)(headPtr).valid
    )).asUInt.andR && headPtr =/= tailPtr 

    val canEnq = tailPtr + 1.U =/= headPtr

    val mem5_ldu_results = PipeReg(io.mem4_ldu_result, false.B, io.flush)
    val mem5_ldu_valid_mask = VecInit(mem5_ldu_results.map(_.valid))
    val mem5_ldu_prefix_sums = WireInit(VecInit(Seq.fill(p(LDU_NUM))(0.U(bankBits.W))))
    val enqCount = mem5_ldu_results.map(_.valid).reduce(_||_).asUInt

    val mem5_ldu_enqs = WireInit(VecInit(
        Seq.fill(p(LDU_NUM))(0.U.asTypeOf(new LoadWbQueueItem))
    ))

    mem5_ldu_prefix_sums(0) := 0.U
    for (i <- 1 until p(LDU_NUM)) {
        mem5_ldu_prefix_sums(i) := mem5_ldu_prefix_sums(i - 1) + mem5_ldu_valid_mask(i - 1).asUInt
    }

    for(i <- 0 until p(LDU_NUM)) {
        when(mem5_ldu_results(i).valid) {
            mem5_ldu_enqs(mem5_ldu_prefix_sums(i)) := mem5_ldu_results(i)
        }
    }

    for(i <- 0 until bankNum) {
        when(mem5_ldu_enqs(i).valid && canEnq) {
            loadBaseInfoBanks(i)(tailPtr).valid := mem5_ldu_enqs(i).valid
            loadBaseInfoBanks(i)(tailPtr).mask := mem5_ldu_enqs(i).mask
            loadBaseInfoBanks(i)(tailPtr).HasRd := mem5_ldu_enqs(i).HasRd
            loadBaseInfoBanks(i)(tailPtr).is_sign_ext := mem5_ldu_enqs(i).is_sign_ext
            loadBaseInfoBanks(i)(tailPtr).pd := mem5_ldu_enqs(i).pd
            loadBaseInfoBanks(i)(tailPtr).rd := mem5_ldu_enqs(i).rd
            loadBaseInfoBanks(i)(tailPtr).bankId := mem5_ldu_enqs(i).bankId
        }
    }

    for(b <- 0 until bankNum) {
        for(i <- 0 until perBankSize) {
            val refill_hit = loadCacheInfoBanks(b)(i).valid &&
                loadCacheInfoBanks(b)(i).pa.GetPPN === io.dcache_refill_resp.pa.GetPPN &&
                io.dcache_refill_resp.valid

            val refill_cacheLine = WireInit(0.U.asTypeOf(new DCacheLine))
            when(io.dcache_refill_resp.valid) {
                refill_cacheLine.datas := io.dcache_refill_resp.data
            }

            val refill_way = WireInit(0.U(log2Ceil(p(L1CacheWayNum)).W))
            when(io.dcache_way_refill_resp.valid) {
                refill_way := io.dcache_way_refill_resp.way
            }

            when(i.U === tailPtr && canEnq) {
                loadCacheInfoBanks(b)(i.U).valid := mem5_ldu_enqs(b).valid
                loadCacheInfoBanks(b)(i.U).hit := mem5_ldu_enqs(b).hit || refill_hit
                loadCacheInfoBanks(b)(i.U).pa := mem5_ldu_enqs(b).pa
                loadCacheInfoBanks(b)(i.U).cacheLine := Mux(refill_hit, refill_cacheLine, mem5_ldu_enqs(b).cacheLine)
                loadCacheInfoBanks(b)(i.U).robLoc := mem5_ldu_enqs(b).robLoc
                loadCacheInfoBanks(b)(i.U).hitWay := Mux(refill_hit, refill_way, mem5_ldu_enqs(b).hitWay)                
            }.otherwise {
                loadCacheInfoBanks(b)(i.U).hit := loadCacheInfoBanks(b)(i.U).hit || refill_hit
                loadCacheInfoBanks(b)(i.U).cacheLine := Mux(refill_hit, refill_cacheLine, loadCacheInfoBanks(b)(i.U).cacheLine)
            }
        }
    }

    for(b <- 0 until bankNum) {
        when(canDeq && loadCacheInfoBanks(b)(headPtr).valid) {
            io.mem5_ldu_deqs(b).valid := true.B
            io.mem5_ldu_deqs(b).hit := loadCacheInfoBanks(b)(headPtr).hit
            io.mem5_ldu_deqs(b).pa := loadCacheInfoBanks(b)(headPtr).pa
            io.mem5_ldu_deqs(b).cacheLine := loadCacheInfoBanks(b)(headPtr).cacheLine
            io.mem5_ldu_deqs(b).robLoc := loadCacheInfoBanks(b)(headPtr).robLoc
            io.mem5_ldu_deqs(b).mask := loadBaseInfoBanks(b)(headPtr).mask
            io.mem5_ldu_deqs(b).bankId := loadBaseInfoBanks(b)(headPtr).bankId
            io.mem5_ldu_deqs(b).hitWay := loadCacheInfoBanks(b)(headPtr).hitWay
            io.mem5_ldu_deqs(b).HasRd := loadBaseInfoBanks(b)(headPtr).HasRd
            io.mem5_ldu_deqs(b).pd := loadBaseInfoBanks(b)(headPtr).pd
            io.mem5_ldu_deqs(b).rd := loadBaseInfoBanks(b)(headPtr).rd
            io.mem5_ldu_deqs(b).is_sign_ext := loadBaseInfoBanks(b)(headPtr).is_sign_ext
        }
    }

    headPtr := headPtr + canDeq.asUInt
    tailPtr := Mux(canEnq, tailPtr + enqCount, tailPtr)

    io.mem5_ldu_wb_stall := !canDeq
}

