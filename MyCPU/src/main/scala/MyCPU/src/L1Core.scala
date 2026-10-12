package MyCPU

import chisel3._
import chisel3.util._

import MyCPU.config._
import MyCPU.io._
import MyCPU.frontend._
import MyCPU.middleend._
import MyCPU.backend._
import MyCPU.cache._
import org.chipsalliance.cde.config._
import MyCPU.utils._

// including L1 Cache and crossbar
// to do: processor state broadcast to prev stages
// processor state broadcast through wire use piperegs per 4 cycles
// [4 x percycle delay] pipe processor statejj
class L1Core(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    val axi = new AXI4(p(ADDR_WIDTH), p(DATA_WIDTH), 4)
  })

  val frontEnd = Module(new FrontEnd)
  val middleEnd = Module(new MiddleEnd)
  val backEnd = Module(new BackEnd)

  val icache = Module(new ICache)
  val itlb = Module(new ITLB(32))

  val dcache = Module(new DCache)
  val dtlb = Module(new DTLB(64))

  val middle_end_broadcaster = Module(new RedirectBroadcaster)
  val front_end_broadcaster = Module(new RedirectBroadcaster)

  // to do: axi crossbar
  val crossbar = Module(new AXI4Crossbar(1, 2))

  // frontend <> icache
  frontEnd.io.icache_stall_req := icache.io.if2_icache_stall_req
  frontEnd.io.if2_icache_hit := icache.io.if2_icache_hit
  frontEnd.io.if2_insts := icache.io.if2_icacheline_resp
  frontEnd.io.if2_predecode := icache.io.if2_predecode
  frontEnd.io.if2_mshr_id := icache.io.if2_mshr_id
  frontEnd.io.if2_mshr_id_valid := icache.io.if2_mshr_id_valid

  icache.io.if0_icache_req := frontEnd.io.if0_icache_req

  icache.io.if2_stall := frontEnd.io.if2_stall
  icache.io.if1_stall := frontEnd.io.if1_stall

  icache.io.if1_flush := frontEnd.io.if1_flush
  icache.io.if2_flush := frontEnd.io.if2_flush

  frontEnd.io.if5_mshr_id := icache.io.if5_mshr_id
  frontEnd.io.if5_mshr_id_valid := icache.io.if5_mshr_id_valid
  frontEnd.io.if5_icache_refill_resp := icache.io.if5_icache_refill_resp

  // frontend <> itlb
  itlb.io.req := frontEnd.io.if0_itlb_req
  frontEnd.io.if1_itlb_resp := itlb.io.resp

  // itlb > icache
  icache.io.if1_itlb_resp := itlb.io.resp

  // icache <> crossbar
  icache.io.axi4_master.ar <> crossbar.io.slave_axi4s(0).ar
  icache.io.axi4_master.r <> crossbar.io.slave_axi4s(0).r
  icache.io.axi4_master.aw <> crossbar.io.slave_axi4s(0).aw
  icache.io.axi4_master.w <> crossbar.io.slave_axi4s(0).w
  icache.io.axi4_master.b <> crossbar.io.slave_axi4s(0).b

  // frontend <> middleend
  middleEnd.io.if5_pc_vec_i := frontEnd.io.if5_pc_vec
  middleEnd.io.if5_inst_i := frontEnd.io.if5_inst
  middleEnd.io.if5_instValid_i := frontEnd.io.if5_instValid
  middleEnd.io.if5_br_pred_info_vec_i := frontEnd.io.if5_br_pred_info_vec

  frontEnd.io.stall_from_backpipe := middleEnd.io.rename1_stall || middleEnd.io.rename2_stall

  // to do: jal redirect from decode

  // middleend <> backend
  backEnd.io.ldu_uops := middleEnd.io.load_uops
  backEnd.io.stu_uops := middleEnd.io.store_uops
  backEnd.io.alu_uops := middleEnd.io.alu_uops
  backEnd.io.mult_uops := middleEnd.io.mult_uops
  backEnd.io.div_uops := middleEnd.io.div_uops
  backEnd.io.rem_uops := middleEnd.io.rem_uops
  backEnd.io.sys_uops := middleEnd.io.sys_uops

  backEnd.io.freeRegIdBufferResp := middleEnd.io.freeRegIdBufferResp
  backEnd.io.busyTableCheckPoint := middleEnd.io.checkPointInfo
  backEnd.io.dispatch1_uops := middleEnd.io.inorder_uops
  
  middleEnd.io.busyTables := backEnd.io.busyTableCheckPoints
  middleEnd.io.commit_regid_write_back := backEnd.io.freeRegIdBufferUpdate
  middleEnd.io.commit_robId_write_back := backEnd.io.freeRobIdBufferUpdate
  middleEnd.io.inorder_locs := backEnd.io.inorder_locs

  middleEnd.io.stall_from_backpipe := backEnd.io.issue0_stall
  middleEnd.io.loadRAWHarvard <> backEnd.io.load_raw_harvards

  // backend <> dcache

  // backend <> dtlb

  // dcache <> dcache wb queue
  

  // dcache <> crossbar
  dcache.io.axi4_master.ar <> crossbar.io.slave_axi4s(1).ar
  dcache.io.axi4_master.r <> crossbar.io.slave_axi4s(1).r
  dcache.io.axi4_master.aw <> crossbar.io.slave_axi4s(1).aw
  dcache.io.axi4_master.w <> crossbar.io.slave_axi4s(1).w
  dcache.io.axi4_master.b <> crossbar.io.slave_axi4s(1).b

  // crossbar <> io
  io.axi.ar <> crossbar.io.master_axi4s(0).ar
  io.axi.r <> crossbar.io.master_axi4s(0).r
  io.axi.aw <> crossbar.io.master_axi4s(0).aw
  io.axi.w <> crossbar.io.master_axi4s(0).w
  io.axi.b <> crossbar.io.master_axi4s(0).b
}