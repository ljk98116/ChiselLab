package MyCPU.backend

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._
import MyCPU.cache._

class StoreCacheInfo(implicit p: Parameters) extends Bundle {
    val valid = Bool()
    val hit = Bool()
    val pa = new PhysicalAddress
    val cacheLine = new DCacheLine
    val robLoc = UInt(p(ROBID_WIDTH).W)
    val hitWay = UInt(log2Ceil(p(L1CacheWayNum)).W) 
}

class StoreBaseInfo(implicit p: Parameters) extends Bundle {
    val readPortNum = p(LDU_NUM)
    val writePortNum = p(STU_NUM)
    val bankNum = writePortNum + readPortNum
    val bankBits = log2Ceil(bankNum)

    val valid = Bool()    
    val mask = UInt(p(DATA_WIDTH).W)
    val wdata = UInt(p(DATA_WIDTH).W)   
    val bankId = UInt(bankBits.W)
}

class StoreWbQueue(size: Int)(implicit p: Parameters) extends Module {
    val bankNum = p(STU_NUM)
    val bankBits = log2Ceil(bankNum)
    val ptrWidth = log2Ceil(size)
    val perBankSize = size / bankNum
    val rowPtrWidth = log2Ceil(perBankSize)

    val io = IO(new Bundle {
        val flush = Input(Bool())
        val mem4_stu_result = Input(Vec(p(STU_NUM), new StoreWbQueueItem))
        val mem5_stu_deqs = Output(Vec(p(STU_NUM), new StoreWbQueueItem))
        val dcache_refill_resp = Input(new DCacheRefillResp)
        val dcache_way_refill_resp = Input(new ReplacePolicyRefillRespInfo)
        // val mem5_stu_wb_stall = Output(Bool())
    })

    val StoreBaseInfoBanks = Seq.fill(bankNum)(
        RegInit(VecInit(
            Seq.fill(perBankSize)(0.U.asTypeOf(new StoreBaseInfo))
        ))
    )

    val StoreCacheInfoBanks = Seq.fill(bankNum)(
        RegInit(VecInit(
            Seq.fill(perBankSize)(0.U.asTypeOf(new StoreCacheInfo))
        ))
    )

    val headPtr = RegInit(0.U(rowPtrWidth.W))
    val tailPtr = RegInit(0.U(rowPtrWidth.W))

    val canDeq = VecInit((0 until bankNum).map(i => 
        (StoreCacheInfoBanks(i)(headPtr).valid && StoreCacheInfoBanks(i)(headPtr).hit) || !StoreCacheInfoBanks(i)(headPtr).valid
    )).asUInt.andR && headPtr =/= tailPtr 

    val canEnq = tailPtr + 1.U =/= headPtr

    val mem5_stu_results = PipeReg(io.mem4_stu_result, false.B, io.flush)
    val mem5_stu_valid_mask = VecInit(mem5_stu_results.map(_.valid))
    val mem5_stu_prefix_sums = WireInit(VecInit(Seq.fill(p(STU_NUM))(0.U(bankBits.W))))
    val enqCount = mem5_stu_results.map(_.valid).reduce(_||_).asUInt

    val mem5_stu_enqs = WireInit(VecInit(
        Seq.fill(p(STU_NUM))(0.U.asTypeOf(new StoreWbQueueItem))
    ))

    mem5_stu_prefix_sums(0) := 0.U
    for (i <- 1 until p(STU_NUM)) {
        mem5_stu_prefix_sums(i) := mem5_stu_prefix_sums(i - 1) + mem5_stu_valid_mask(i - 1).asUInt
    }

    for(i <- 0 until p(STU_NUM)) {
        when(mem5_stu_results(i).valid) {
            mem5_stu_enqs(mem5_stu_prefix_sums(i)) := mem5_stu_results(i)
        }
    }

    for(i <- 0 until bankNum) {
        when(mem5_stu_enqs(i).valid && canEnq) {
            StoreBaseInfoBanks(i)(tailPtr).valid := mem5_stu_enqs(i).valid
            StoreBaseInfoBanks(i)(tailPtr).mask := mem5_stu_enqs(i).mask
            StoreBaseInfoBanks(i)(tailPtr).wdata := mem5_stu_enqs(i).wdata
            StoreBaseInfoBanks(i)(tailPtr).bankId := mem5_stu_enqs(i).bankId
        }
    }

    for(b <- 0 until bankNum) {
        for(i <- 0 until perBankSize) {
            val refill_hit = StoreCacheInfoBanks(b)(i).valid &&
                StoreCacheInfoBanks(b)(i).pa.GetPPN === io.dcache_refill_resp.pa.GetPPN &&
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
                StoreCacheInfoBanks(b)(i.U).valid := mem5_stu_enqs(b).valid
                StoreCacheInfoBanks(b)(i.U).hit := mem5_stu_enqs(b).hit || refill_hit
                StoreCacheInfoBanks(b)(i.U).pa := mem5_stu_enqs(b).pa
                StoreCacheInfoBanks(b)(i.U).cacheLine := Mux(refill_hit, refill_cacheLine, mem5_stu_enqs(b).cacheLine)
                StoreCacheInfoBanks(b)(i.U).robLoc := mem5_stu_enqs(b).robLoc
                StoreCacheInfoBanks(b)(i.U).hitWay := Mux(refill_hit, refill_way, mem5_stu_enqs(b).hitWay)                
            }.otherwise {
                StoreCacheInfoBanks(b)(i.U).hit := StoreCacheInfoBanks(b)(i.U).hit || refill_hit
                StoreCacheInfoBanks(b)(i.U).cacheLine := Mux(refill_hit, refill_cacheLine, StoreCacheInfoBanks(b)(i.U).cacheLine)
            }
        }
    }

    for(b <- 0 until bankNum) {
        when(canDeq && StoreCacheInfoBanks(b)(headPtr).valid) {
            io.mem5_stu_deqs(b).valid := true.B
            io.mem5_stu_deqs(b).hit := StoreCacheInfoBanks(b)(headPtr).hit
            io.mem5_stu_deqs(b).pa := StoreCacheInfoBanks(b)(headPtr).pa
            io.mem5_stu_deqs(b).cacheLine := StoreCacheInfoBanks(b)(headPtr).cacheLine
            io.mem5_stu_deqs(b).robLoc := StoreCacheInfoBanks(b)(headPtr).robLoc
            io.mem5_stu_deqs(b).mask := StoreBaseInfoBanks(b)(headPtr).mask
            io.mem5_stu_deqs(b).wdata := StoreBaseInfoBanks(b)(headPtr).wdata
            io.mem5_stu_deqs(b).bankId := StoreBaseInfoBanks(b)(headPtr).bankId
            io.mem5_stu_deqs(b).hitWay := StoreCacheInfoBanks(b)(headPtr).hitWay
        }
        .otherwise {
            io.mem5_stu_deqs(b) := 0.U.asTypeOf(new StoreWbQueueItem)
        }
    }

    headPtr := headPtr + canDeq.asUInt
    tailPtr := Mux(canEnq, tailPtr + enqCount, tailPtr)

    // io.mem5_stu_wb_stall := !canDeq
}