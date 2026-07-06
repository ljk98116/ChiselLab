package MyCPU.utils

import chisel3._
import chisel3.util._

import chisel3.stage.DesignAnnotation
import chisel3.experimental.ChiselAnnotation
import firrtl.annotations.{SingleTargetAnnotation, ReferenceTarget, Annotation}

case class SignalMapAnnotation(target: ReferenceTarget, sourceInfo: String)
    extends SingleTargetAnnotation[ReferenceTarget] {
  def duplicate(n: ReferenceTarget) = this.copy(target = n)
}