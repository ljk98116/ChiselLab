package MyCPU.frontend.BPU

import chisel3._
import chisel3.util._

import MyCPU.config._
import MyCPU.io._
import org.chipsalliance.cde.config._
import MyCPU.utils._

// for RAS, call must jump, but may be invalid because of prev branch pred
// only update the first pc which is call or ret

// 1. isCall, prev pred has no branch => 
// update RAS with pc + 4(pred is call && taken), record the old rasTopPtr
// 1.1 prev pred no branch[right]: no need to recover RAS
// 1.2 prev pred no branch[wrong]: recover RAS with old rasTopPtr

// 2. isCall, prev pred has branch => not update RAS, but record rasTopPtr + 1
// 2.1 prev pred has branch[right]: no need to recover RAS
// 2.2 prev pred has branch[wrong]: no need to recover RAS, 
// because now we focus the previous branch, when prev pred has no branch turn to case 1

// 3. isReturn && prev pred has no branch =>
// update rasTopPtr with rasTopPtr - 1 and use rasTopPtr
// 3.1 prev pred has no branch[right]: no need to recover RAS
// 3.2 prev pred has no branch[wrong]: recover RAS with old rasTopPtr

// 4. isReturn && prev pred has branch => not update RAS, but record rasTopPtr
// 4.1 prev pred has branch[right]: no need to recover RAS
// 4.2 prev pred has branch[wrong]: no need to recover RAS, 
// because now we focus the previous branch, when prev pred has no branch turn to case 3
class RAS(implicit p: Parameters) extends Module {
  val ptrWidth = log2Ceil(p(RASParam).RASSize + 1)

  val io = IO(new Bundle {
    // 推测更新 (来自if3)
    val if3_isCall    = Input(Bool())
    val if3_isReturn  = Input(Bool())
    val if3_pc        = Input(UInt(p(ADDR_WIDTH).W))
    val if3_pcValid   = Input(Bool())
    // 推测结果输出
    val rasTopPtr     = Output(UInt(ptrWidth.W))
    val rasTop        = Output(UInt(p(ADDR_WIDTH).W))
    // 重定向恢复 (来自提交阶段)
    val redirectValid     = Input(Bool())
    val redirectIsCall    = Input(Bool())
    val redirectIsReturn  = Input(Bool())
    val redirectRasTopPtr = Input(UInt(ptrWidth.W))
    val redirectPC        = Input(UInt(p(ADDR_WIDTH).W))
    // debug
    val pushCount = Output(UInt(32.W))
    val popCount = Output(UInt(32.W))
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

  // 优先级：重定向 > if3
  val isRedirectCall   = io.redirectValid && io.redirectIsCall
  val isRedirectReturn = io.redirectValid && io.redirectIsReturn
  val isif3Call        = io.if3_pcValid && io.if3_isCall
  val isif3Return      = io.if3_pcValid && io.if3_isReturn

  // 互斥的选中事件（高优先级优先）
  val selRedirectCall   = isRedirectCall
  val selRedirectReturn = isRedirectReturn && !selRedirectCall
  val selif3Call        = isif3Call    && !(selRedirectCall || selRedirectReturn)
  val selif3Return      = isif3Return  && !(selRedirectCall || selRedirectReturn || selif3Call)

  // 根据选中事件计算新栈顶指针、写数据、写地址、是否写内存
    // 计算新栈顶指针
    val newPtr = MuxCase(rasTopPtr, Seq(
        selRedirectCall  -> incPtr(io.redirectRasTopPtr),
        selRedirectReturn -> decPtr(io.redirectRasTopPtr),
        io.redirectValid -> io.redirectRasTopPtr,
        selif3Call -> incPtr(rasTopPtr),
        selif3Return -> decPtr(rasTopPtr)
    ))

    // 需要写内存的数据（仅调用时有效）
    val writeData = MuxCase(0.U, Seq(
        selRedirectCall -> (io.redirectPC + 4.U),
        selif3Call -> (io.if3_pc + 4.U)
    ))

    // 写内存的地址（仅调用时有效）
    val writeAddr = MuxCase(0.U, Seq(
        selRedirectCall -> io.redirectRasTopPtr,
        selif3Call -> rasTopPtr
    ))

    // 是否执行写操作
    val doWrite = selRedirectCall || selif3Call

    // 执行写内存
    when(doWrite) {
        rasMem.write(writeAddr, writeData)
    }

    // 写内存（仅调用时）
    when(doWrite) {
        rasMem.write(writeAddr, writeData)
    }

    // 更新栈顶指针（任一事件发生时）
    when(selRedirectCall || selRedirectReturn || selif3Call || selif3Return || io.redirectValid) {
        rasTopPtr := newPtr
    }

    // 输出当前栈顶地址（组合逻辑读）
    io.rasTop := Mux(rasTopPtr === 0.U, 0.U, rasMem.read(rasTopPtr - 1.U))
    io.rasTopPtr := rasTopPtr

    // 调试信息
    val pushCnt = RegInit(0.U(32.W))
    val popCnt  = RegInit(0.U(32.W))

    when(io.redirectValid || io.redirectIsCall || io.redirectIsReturn) {
        printf(p"""[RAS-RESTORE] oldPtr=${rasTopPtr} newPtr=${newPtr}\n""")
    }

    when(io.if3_pcValid && io.if3_isCall) {
        pushCnt := pushCnt + 1.U
        printf(p"""[RAS-PUSH] pc=0x${io.if3_pc}%x push=${pushCnt}\n""")
    }
    when(io.if3_pcValid && io.if3_isReturn) {
        popCnt := popCnt + 1.U
        printf(p"""[RAS-POP]  pc=0x${io.if3_pc}%x pop=${popCnt}\n""")
    }

    // 加到 io
    val pushCount = Output(UInt(32.W))
    val popCount  = Output(UInt(32.W))
    io.pushCount := pushCnt
    io.popCount  := popCnt
    // printf(p"RAS Top: ${rasTopPtr} Event: ${Cat(selRedirectCall, selRedirectReturn, selif3Call, selif3Return)} Value: ${io.rasTop}\n")
}