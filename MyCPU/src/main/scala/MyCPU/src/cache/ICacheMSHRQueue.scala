package MyCPU.cache

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._
import MyCPU.utils._

// mshr enq a paddr, output a mshr id for enq paddr
// mshr queue
class ICacheMSHRQueue(size: Int)(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val enq = Input(new CacheRefillReq)
        val mshr_id = Output(UInt(log2Ceil(size).W))
        val mshr_id_valid = Output(Bool())

        val deq_ready = Input(Bool())
        val deq = Output(new ICacheMSHREntry)
        val enq_ready = Output(Bool())
    })
    
    val mshr_queue = new LargeBank(clock, reset, size, new ICacheMSHREntry)
    val head_ptr = RegInit(0.U(log2Ceil(size).W))
    val tail_ptr = RegInit(0.U(log2Ceil(size).W))
    val count = RegInit(0.U((log2Ceil(size) + 1).W))

    val enq_ready = count =/= size.U
    val deq_ready = count =/= 0.U

    val enq_item = WireInit(0.U.asTypeOf(new ICacheMSHREntry))
    val deq_item = WireInit(0.U.asTypeOf(new ICacheMSHREntry))

    val enq_can_merge_vec = WireInit(VecInit((0 until size).map(i => {
        val item = mshr_queue.read(i.U).asTypeOf(new ICacheMSHREntry)
        item.valid && item.paddr.GetPPN === io.enq.pa.GetPPN
    })))

    val enq_can_merge = WireInit((0 until size).map(i => {
        val item = mshr_queue.read(i.U).asTypeOf(new ICacheMSHREntry)
        item.valid && item.paddr.GetPPN === io.enq.pa.GetPPN
    }).reduce(_ || _))

    val enq_merge_mshr_id = PriorityEncoder(enq_can_merge_vec)
    
    // not alloc mshr item but use previous mshr, 
    // when icache miss share with a previous inst pack icache miss
    when(io.enq.valid && enq_can_merge) {
        io.mshr_id := enq_merge_mshr_id
        io.mshr_id_valid := false.B
        io.enq_ready := true.B
    }.otherwise {
        io.mshr_id := tail_ptr
        io.mshr_id_valid := enq_ready && io.enq.valid
        io.enq_ready := enq_ready
    }

    io.mshr_id := tail_ptr
    io.mshr_id_valid := enq_ready && io.enq.valid
    io.enq_ready := enq_ready

    enq_item.paddr := io.enq.pa
    enq_item.valid := true.B
    enq_item.mshr_id := tail_ptr
    
    when(io.enq.valid && enq_ready && !enq_can_merge) {
        mshr_queue.write(tail_ptr, ~(0.U), enq_item.asUInt)
        tail_ptr := tail_ptr + 1.U
    }

    when(io.deq_ready && deq_ready) {
        head_ptr := head_ptr + 1.U
    }

    when((io.enq.valid && enq_ready && !enq_can_merge) && io.deq_ready) {
        count := count
    }.elsewhen(io.enq.valid && enq_ready && !enq_can_merge) {
        count := count + 1.U
    }.elsewhen(io.deq_ready && deq_ready) {
        count := count - 1.U
    }

    deq_item := mshr_queue.read(head_ptr)
    io.deq := deq_item
}