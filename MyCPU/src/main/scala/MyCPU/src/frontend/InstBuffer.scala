package MyCPU.frontend

import chisel3._ 
import chisel3.util._ 
import MyCPU.config._ 
import MyCPU.io._ 
import MyCPU.utils._
import org.chipsalliance.cde.config._

// if4 enq
// case 1: check mshr valid, if valid, enq
// case 2: check mshr not valid and queue not empty, enq
// case 3: check mshr not valid and queue empty, only do pipereg

// update every inst buffer entry when icache do refill
// fill the insts with right data
// area opt may be a problem
class InstBuffer(size: Int)(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val flush = Input(Bool())
        val stall = Input(Bool())
        // from if4
        val if4_pc_i = Input(Vec(p(FETCH_WIDTH), UInt(p(ADDR_WIDTH).W)))
        val if4_inst_i = Input(Vec(p(FETCH_WIDTH), UInt(p(DATA_WIDTH).W)))
        val if4_instValid_i = Input(Vec(p(FETCH_WIDTH), Bool()))
        val if4_branch_result_i = Input(Vec(p(FETCH_WIDTH), new BranchPredInfo))
        val if4_pa_i = Input(new PhysicalAddress)
        val if4_mshr_id_i = Input(UInt(log2Ceil(p(ICacheMSHRNum)).W))
        val if4_mshr_id_valid_i = Input(Bool())
        val if4_icache_hit_i = Input(Bool())
        // to if4
        val canEnq = Output(Bool())
        // from icache related
        val refill_pa_i = Input(new PhysicalAddress)
        val refill_valid_i = Input(Bool())
        val refill_insts_i = Input(Vec(p(L1CacheLineLength), UInt(p(INST_WIDTH).W)))
        val refill_mshr_id_i = Input(UInt(log2Ceil(p(ICacheMSHRNum)).W))
        val refill_mshr_id_valid_i = Input(Bool())

        val if5_inst_o = Output(Vec(p(FETCH_WIDTH), UInt(p(INST_WIDTH).W)))
        val if5_instValid_o = Output(Vec(p(FETCH_WIDTH), Bool()))
        val if5_branch_result_o = Output(Vec(p(FETCH_WIDTH), new BranchPredInfo))
        val if5_pc_o = Output(Vec(p(FETCH_WIDTH), UInt(p(ADDR_WIDTH).W)))
    })

    val ptrWidth = log2Ceil(size)

    // memory: use a vector of registers for entries
    val inst_buffer_entries = RegInit(VecInit(Seq.fill(size)(0.U.asTypeOf(new InstBufferEntry))))

    val head_ptr = RegInit(0.U(ptrWidth.W))
    val tail_ptr = RegInit(0.U(ptrWidth.W))
    val freeCount = RegInit(size.U((ptrWidth + 1).W))

    val canEnq = freeCount =/= 0.U 
    val canDeq = freeCount =/= size.U

    io.canEnq := canEnq

    val enq_valid = canEnq && io.if4_instValid_i.asUInt.orR && !io.flush && !io.stall

    val deq_item = WireInit(0.U.asTypeOf(new InstBufferEntry))
    for(i <- 0 until size) {
        when(i.U === head_ptr) {
            deq_item := inst_buffer_entries(i)
        }
    }

    val deq_valid = canDeq && deq_item.hit && !io.flush && !io.stall

    for(i <- 0 until size) {
        val refill_hit = io.refill_mshr_id_valid_i && 
            io.refill_mshr_id_i === inst_buffer_entries(i).mshr &&
            inst_buffer_entries(i).valid && !inst_buffer_entries(i).hit

        when(io.flush) {
            inst_buffer_entries(i) := 0.U.asTypeOf(new InstBufferEntry)
        }
        .elsewhen(i.U === tail_ptr && enq_valid) {
            inst_buffer_entries(i).pc_vec := io.if4_pc_i
            inst_buffer_entries(i).inst_valid_vec := io.if4_instValid_i
            inst_buffer_entries(i).inst_vec := io.if4_inst_i
            inst_buffer_entries(i).pa := io.if4_pa_i
            inst_buffer_entries(i).branch_info := io.if4_branch_result_i
            inst_buffer_entries(i).hit := io.if4_icache_hit_i
            inst_buffer_entries(i).mshr := io.if4_mshr_id_i
            inst_buffer_entries(i).mshr_valid := io.if4_mshr_id_valid_i
            inst_buffer_entries(i).offsets := VecInit((0 until p(FETCH_WIDTH)).map(b => 
                b.U + io.if4_pa_i.bits(p(L1CacheOffsetBits) + p(FETCH_OFFSET) - 1, p(FETCH_OFFSET))    
            ))
        }
        .otherwise {
            inst_buffer_entries(i).hit := Mux(refill_hit, true.B, inst_buffer_entries(i).hit)
            for(b <- 0 until p(FETCH_WIDTH)) {
                inst_buffer_entries(i).inst_vec(b) := Mux(
                    refill_hit, io.refill_insts_i(inst_buffer_entries(i).offsets(b)), inst_buffer_entries(i).inst_vec(b))
            }
        }
    }

    when(io.flush) {
        freeCount := size.U
    }
    .elsewhen(enq_valid && !deq_valid) {
        freeCount := freeCount - 1.U
    }
    .elsewhen(!enq_valid && deq_valid) {
        freeCount := freeCount + 1.U
    }
    
    head_ptr := Mux(
        io.flush, 0.U, head_ptr + deq_valid
    )

    tail_ptr := Mux(
        io.flush, 0.U, tail_ptr + enq_valid
    )

    io.if5_pc_o := Mux(deq_valid, deq_item.pc_vec, VecInit(Seq.fill(p(FETCH_WIDTH))(0.U(p(ADDR_WIDTH).W))))
    io.if5_branch_result_o := Mux(deq_valid, deq_item.branch_info, VecInit(Seq.fill(p(FETCH_WIDTH))(0.U.asTypeOf(new BranchPredInfo))))
    io.if5_instValid_o := Mux(deq_valid, deq_item.inst_valid_vec, VecInit(Seq.fill(p(FETCH_WIDTH))(false.B)))
    io.if5_inst_o := Mux(deq_valid, deq_item.inst_vec, VecInit(Seq.fill(p(FETCH_WIDTH))(0.U(p(INST_WIDTH).W))))
}
