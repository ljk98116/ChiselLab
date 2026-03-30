package MyCPU.frontend.BPU

import chisel3._
import chisel3.util._

import MyCPU.config._
import MyCPU.io._
import org.chipsalliance.cde.config._
import MyCPU.utils._

class RAS(implicit p: Parameters) extends Module {
  val ptrWidth = log2Ceil(p(RASParam).RASSize)

  val io = IO(new Bundle {
    // 推测更新 (来自if2)
    val if2_isCall    = Input(Bool())
    val if2_isReturn  = Input(Bool())
    val if2_pc        = Input(UInt(p(ADDR_WIDTH).W))
    val if2_pcValid   = Input(Bool())
    // 推测结果输出
    val rasTopPtr     = Output(UInt(ptrWidth.W))
    val rasTop        = Output(UInt(p(ADDR_WIDTH).W))
    // 重定向恢复 (来自提交阶段)
    val redirectValid     = Input(Bool())
    val redirectIsCall    = Input(Bool())
    val redirectIsReturn  = Input(Bool())
    val redirectRasTopPtr = Input(UInt(ptrWidth.W))
    val redirectPC        = Input(UInt(p(ADDR_WIDTH).W))
  })

  val rasMem = Mem(p(RASParam).RASSize, UInt(p(ADDR_WIDTH).W)) // 返回地址栈
  val rasTopPtr = RegInit(0.U(ptrWidth.W))

  // 辅助函数：栈顶指针增/减，处理边界
  def incPtr(ptr: UInt): UInt = {
    Mux(ptr === (p(RASParam).RASSize - 1).U, ptr, ptr + 1.U) // 满则不变
  }
  def decPtr(ptr: UInt): UInt = {
    Mux(ptr === 0.U, ptr, ptr - 1.U) // 空则不变
  }

  // 优先级：重定向 > if2
  val isRedirectCall   = io.redirectValid && io.redirectIsCall
  val isRedirectReturn = io.redirectValid && io.redirectIsReturn
  val isIf2Call        = io.if2_pcValid && io.if2_isCall
  val isIf2Return      = io.if2_pcValid && io.if2_isReturn

  // 互斥的选中事件（高优先级优先）
  val selRedirectCall   = isRedirectCall
  val selRedirectReturn = isRedirectReturn && !selRedirectCall
  val selIf2Call        = isIf2Call    && !(selRedirectCall || selRedirectReturn)
  val selIf2Return      = isIf2Return  && !(selRedirectCall || selRedirectReturn || selIf2Call)

  // 根据选中事件计算新栈顶指针、写数据、写地址、是否写内存
    // 计算新栈顶指针
    val newPtr = MuxCase(rasTopPtr, Seq(
        selRedirectCall -> incPtr(io.redirectRasTopPtr),
        selRedirectReturn -> decPtr(io.redirectRasTopPtr),
        selIf2Call -> incPtr(rasTopPtr),
        selIf2Return -> decPtr(rasTopPtr)
    ))

    // 需要写内存的数据（仅调用时有效）
    val writeData = MuxCase(0.U, Seq(
        selRedirectCall -> (io.redirectPC + 4.U),
        selIf2Call -> (io.if2_pc + 4.U)
    ))

    // 写内存的地址（仅调用时有效）
    val writeAddr = MuxCase(0.U, Seq(
        selRedirectCall -> io.redirectRasTopPtr,
        selIf2Call -> rasTopPtr
    ))

    // 是否执行写操作
    val doWrite = selRedirectCall || selIf2Call

    // 执行写内存
    when(doWrite) {
        rasMem.write(writeAddr, writeData)
    }

    // 更新栈顶指针
    when(selRedirectCall || selRedirectReturn || selIf2Call || selIf2Return) {
        rasTopPtr := newPtr
    }

    // 写内存（仅调用时）
    when(doWrite) {
        rasMem.write(writeAddr, writeData)
    }

    // 更新栈顶指针（任一事件发生时）
    when(selRedirectCall || selRedirectReturn || selIf2Call || selIf2Return) {
        rasTopPtr := newPtr
    }

    // 输出当前栈顶地址（组合逻辑读）
    io.rasTop    := rasMem.read(rasTopPtr)
    io.rasTopPtr := rasTopPtr

    // 调试信息
    // printf(p"RAS Top: ${rasTopPtr} Event: ${Cat(selRedirectCall, selRedirectReturn, selIf2Call, selIf2Return)} Value: ${io.rasTop}\n")
}