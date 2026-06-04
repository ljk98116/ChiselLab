package MyCPU.cache

import chisel3._
import chisel3.util._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

import org.chipsalliance.cde.config._

// multi port access, may have multiple refill reqs and multiple refill resps
class DTLB(size: Int)(implicit p: Parameters) extends Module {
    // sigle way sram ports
    val readPortNum = p(LDU_NUM)
    val writePortNum = p(STU_NUM)
    val bankNum = writePortNum + readPortNum

    val io = IO(new Bundle {
        val flush = Input(Bool())
        val stall_from_backpipe = Input(Bool())
        val mem1_reqs = Input(Vec(bankNum, new DTLBReqInfo))
        val mem2_resps = Output(Vec(bankNum, new DTLBRespInfo))
        val refill_reqs = Output(Vec(bankNum, new DTLBRefillReq))
        val update = Input(Vec(bankNum, new DTLBUpdateInfo)) // merged update
    })

    val tlb = RegInit(VecInit(
        Seq.fill(size)(0.U.asTypeOf(new DTLBEntry))
    ))

    val refill_loc = RegInit(0.U(log2Ceil(size).W))

    for(b <- 0 until bankNum) {
        val req = io.mem1_reqs(b)
        val va = req.addr.asTypeOf(new VirtualAddress)
        val vpn = va.getVPN
        // judge hit judge permission, if hit, get ppn and perm
        val hitMask = VecInit((0 until size).reverse.map(
            index => tlb(index).tag === vpn && tlb(index).valid &&
                ((tlb(index).perm.asTypeOf(new Perm).R && !req.wen) ||
                (tlb(index).perm.asTypeOf(new Perm).W && req.wen))
        ))
        val hit = hitMask.reduce(_ || _)
        val hitIndex = PriorityEncoder(hitMask)
        val hitEntry = tlb(hitIndex)
        val paddr = Cat(hitEntry.ppn, va.bits(11, 0)).asTypeOf(new PhysicalAddress)

        val mem2_paddr = PipeReg(paddr, io.stall_from_backpipe, io.flush)
        val mem2_hit = PipeReg(hit, io.stall_from_backpipe, io.flush)
        val mem2_valid = PipeReg(req.valid, io.stall_from_backpipe, io.flush)

        io.mem2_resps(b).paddr := mem2_paddr
        io.mem2_resps(b).hit := mem2_hit
        io.mem2_resps(b).valid := mem2_valid

        io.refill_reqs(b).va := va
        io.refill_reqs(b).wen := req.wen
        io.refill_reqs(b).valid := !hit && req.valid
    }

    // fifo refill
    refill_loc := refill_loc + PopCount(io.update.map(_.valid))
}

