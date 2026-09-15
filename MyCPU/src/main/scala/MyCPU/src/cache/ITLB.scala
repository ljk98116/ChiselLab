package MyCPU.cache

import chisel3._
import chisel3.util._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

import org.chipsalliance.cde.config._

// fully associate，icache access must not occur 1 fetch 2 pages
// if0 recv req
// if1 get hit/miss, send refill req to PageWalker/ L2 TLB(reserved)
// L2 tlb / 2 pagewalker access DCache
class ITLB(size: Int)(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val flush = Input(Bool())
        val stall_from_backpipe = Input(Bool())
        val req = Input(Vec(p(FETCH_WIDTH), new ITLBReqInfo))
        val update = Input(new ITLBUpdateInfo)
        val resp = Output(Vec(p(FETCH_WIDTH), new ITLBRespInfo))
        val refill_req = Output(new ITLBRefillReq)
    })

    if(p(UseTLB) == false) {
        for(i <- 0 until p(FETCH_WIDTH)) {
            io.resp(i).hit := true.B
            io.resp(i).valid := io.req(i).valid
            io.resp(i).paddr := io.req(i).addr(p(ADDR_WIDTH) - 2, 0).asTypeOf(new PhysicalAddress)
        }
        io.refill_req.va := 0.U.asTypeOf(new VirtualAddress)
        io.refill_req.valid := false.B
    } else {
        val mem = RegInit(VecInit(
            Seq.fill(size)(0.U.asTypeOf(new ITLBEntry))
        ))

        // fifo refill
        val refill_loc = RegInit(0.U(log2Ceil(size).W))
        val if1_req = PipeReg(io.req, io.stall_from_backpipe, io.flush)

        for(i <- 0 until p(FETCH_WIDTH)) {
            val if0_req_va = io.req(i).addr.asTypeOf(new VirtualAddress)
            val if0_req_vpn = if0_req_va.getVPN
            // can read and can exec
            val if0_hitMask = VecInit((0 until size).reverse.map(
                index => mem(index).tag === if0_req_vpn && 
                    mem(index).valid && io.req(i).valid &&
                    mem(index).perm.asTypeOf(new Perm).X &&
                    mem(index).perm.asTypeOf(new Perm).R
            ))
            val if0_hitIndex = OHToUInt(if0_hitMask)
            val if0_hit = if0_hitMask.asUInt.orR
            
            val if1_hit = PipeReg(if0_hit, io.stall_from_backpipe, io.flush)

            val if1_hitIndex = PipeReg(if0_hitIndex, io.stall_from_backpipe, io.flush)
            val if1_hitEntry = mem(if1_hitIndex)

            io.resp(i).hit := if1_hit
            io.resp(i).valid := if1_req(i).valid
            io.resp(i).paddr := Cat(if1_hitEntry.ppn, if1_req(i).addr(11, 0)).asTypeOf(new PhysicalAddress)
            
            // send refill req to page walker
            // io.refill_req(i).va := if1_req.addr.asTypeOf(new VirtualAddress)
            // io.refill_req(i).valid := !if1_hit && if1_req.valid

            // to do, fetch inst exception mark
        }

        io.refill_req.va := if1_req(0).addr.asTypeOf(new VirtualAddress)
        io.refill_req.valid := !VecInit(io.resp.map(_.valid)).asUInt.orR && VecInit(if1_req.map(_.valid)).asUInt.orR

        refill_loc := refill_loc + io.update.valid

        if(p(UseSv32)) {
            mem(refill_loc) := 
                Cat(
                    io.update.valid, io.update.va.getVPN, 
                    io.update.pte.GetPPN, "b000".U, io.update.pte.bits(9, 0)
                ).asTypeOf(new ITLBEntry)
        }
        // N, PBMT
        else {
            mem(refill_loc) := 
                Cat(
                    io.update.valid, io.update.va.getVPN, 
                    io.update.pte.GetPPN, 
                    io.update.pte.bits((new PTE).GetWidth - 1, (new PTE).GetWidth - 3), 
                    io.update.pte.bits(9, 0)
                ).asTypeOf(new ITLBEntry)
        }
    }
}