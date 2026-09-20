package MyCPU.utils

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import MyCPU.config._
import MyCPU.io._

class AXI4Crossbar(masterNum: Int, slaveNum: Int)(implicit p: Parameters) extends Module {
  val addrWidth = p(ADDR_WIDTH)
  val dataWidth = p(DATA_WIDTH)
  val idWidth   = 4

  val io = IO(new Bundle {
    // crossbar 作为 slave 面向"上游 master" → 方向翻转
    val slave_axi4s  = Vec(slaveNum,  Flipped(new AXI4(addrWidth, dataWidth, idWidth)))
    // crossbar 作为 master 面向"下游 slave" → 保持原方向
    val master_axi4s = Vec(masterNum, new AXI4(addrWidth, dataWidth, idWidth))
  })

  val sptrWidth = log2Ceil(slaveNum)

  // ── 1) 先给所有 slave 端口的"后向"信号打默认零 ──
  //    （这些信号是 crossbar 要驱动的 Output 方向，必须先初始化）
  for (s <- 0 until slaveNum) {
    io.slave_axi4s(s).aw.ready := false.B
    io.slave_axi4s(s).w.ready  := false.B
    io.slave_axi4s(s).b.valid  := false.B
    io.slave_axi4s(s).b.bits   := 0.U.asTypeOf(new AXI4B(idWidth))
    io.slave_axi4s(s).ar.ready := false.B
    io.slave_axi4s(s).r.valid  := false.B
    io.slave_axi4s(s).r.bits   := 0.U.asTypeOf(new AXI4R(dataWidth, idWidth))
  }

  // ── 2) 每个 master 端口独立轮询选一个 slave ──
  for (m <- 0 until masterNum) {
    val rrCnt = RegInit(0.U(sptrWidth.W))

    val slaveValidMask = VecInit((0 until slaveNum).map { s =>
      io.slave_axi4s(s).ar.valid || io.slave_axi4s(s).r.ready ||
      io.slave_axi4s(s).aw.valid || io.slave_axi4s(s).w.valid ||
      io.slave_axi4s(s).b.ready
    }).asUInt

    val selMask = slaveValidMask >> rrCnt
    val selHit  = selMask.orR
    // 关键：截断到 sptrWidth 位，既消除 W004，又让索引回绕正确
    val selId   = (PriorityEncoder(selMask) +& rrCnt)(sptrWidth - 1, 0)

    // 前向：slave → master（逐信号 Mux）
    io.master_axi4s(m).aw.valid := Mux(selHit, io.slave_axi4s(selId).aw.valid, false.B)
    io.master_axi4s(m).aw.bits  := Mux(selHit, io.slave_axi4s(selId).aw.bits,
                                      0.U.asTypeOf(new AXI4Aw(addrWidth, idWidth)))
    io.master_axi4s(m).w.valid  := Mux(selHit, io.slave_axi4s(selId).w.valid, false.B)
    io.master_axi4s(m).w.bits   := Mux(selHit, io.slave_axi4s(selId).w.bits,
                                      0.U.asTypeOf(new AXI4W(dataWidth)))
    io.master_axi4s(m).b.ready  := Mux(selHit, io.slave_axi4s(selId).b.ready, false.B)
    io.master_axi4s(m).ar.valid := Mux(selHit, io.slave_axi4s(selId).ar.valid, false.B)
    io.master_axi4s(m).ar.bits  := Mux(selHit, io.slave_axi4s(selId).ar.bits,
                                      0.U.asTypeOf(new AXI4Ar(addrWidth, idWidth)))
    io.master_axi4s(m).r.ready  := Mux(selHit, io.slave_axi4s(selId).r.ready, false.B)

    // 后向：master → slave（动态索引送到被选中的 slave）
    when (selHit) {
      io.slave_axi4s(selId).aw.ready := io.master_axi4s(m).aw.ready
      io.slave_axi4s(selId).w.ready  := io.master_axi4s(m).w.ready
      io.slave_axi4s(selId).b.valid  := io.master_axi4s(m).b.valid
      io.slave_axi4s(selId).b.bits   := io.master_axi4s(m).b.bits
      io.slave_axi4s(selId).ar.ready := io.master_axi4s(m).ar.ready
      io.slave_axi4s(selId).r.valid  := io.master_axi4s(m).r.valid
      io.slave_axi4s(selId).r.bits   := io.master_axi4s(m).r.bits
    }

    val fire = io.master_axi4s(m).aw.fire || io.master_axi4s(m).w.fire ||
              io.master_axi4s(m).b.fire  || io.master_axi4s(m).ar.fire ||
              io.master_axi4s(m).r.fire

    rrCnt := Mux(selHit, (rrCnt +& fire.asUInt)(sptrWidth - 1, 0), 0.U)
  }
}