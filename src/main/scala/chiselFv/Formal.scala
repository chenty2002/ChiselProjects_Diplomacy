package chiselFv

import chisel3._
import chisel3.internal.sourceinfo.SourceInfo
import freechips.rocketchip.diplomacy.LazyModuleImp

// LazyModule


class Formal {

  // this: LazyModule => this.module.auto.clock / this.module.io.clk

  //  this: Module =>

  private lazy val resetCounter = Module(new ResetCounter)
  lazy val timeSinceReset = resetCounter.io.timeSinceReset
  lazy val notChaos = resetCounter.io.notChaos

  import chisel3.{assert => cassert, _}

  def assert(cond: Bool, msg: String = "")
            (implicit sourceInfo: SourceInfo,
             compileOptions: CompileOptions): Unit = {
    when(notChaos) {
      cassert(cond, msg)
    }
  }

  def assertAt(n: UInt, cond: Bool, msg: String = "")
              (implicit sourceInfo: SourceInfo,
               compileOptions: CompileOptions): Unit = {
    when(notChaos && timeSinceReset === n) {
      cassert(cond, msg)
    }
  }

  def assertAfterNStepWhen(cond: Bool, n: Int, asert: Bool, msg: String = "")
                          (implicit sourceInfo: SourceInfo,
                           compileOptions: CompileOptions): Unit = {
    val next = RegInit(VecInit(Seq.fill(n)(false.B)))
    when(cond && notChaos) {
      next(0) := true.B
    }.otherwise {
      next(0) := false.B
    }
    for (i <- 1 until n) {
      next(i) := next(i - 1)
    }
    when(next(n - 1)) {
      assert(asert, msg)
    }
  }

  def assertNextStepWhen(cond: Bool, asert: Bool, msg: String = "")
                        (implicit sourceInfo: SourceInfo,
                         compileOptions: CompileOptions): Unit = {
    assertAfterNStepWhen(cond, 1, asert, msg)
  }

  def assertAlwaysAfterNStepWhen(cond: Bool, n: Int, asert: Bool, msg: String = "")
                                (implicit sourceInfo: SourceInfo,
                                 compileOptions: CompileOptions): Unit = {
    val next = RegInit(VecInit(Seq.fill(n)(false.B)))
    when(cond && notChaos) {
      next(0) := true.B
    }
    for (i <- 1 until n) {
      next(i) := next(i - 1)
    }
    when(next(n - 1)) {
      assert(asert, msg)
    }

  }

  def past[T <: Data](value: T, n: Int)(block: T => Any)
                     (implicit sourceInfo: SourceInfo,
                      compileOptions: CompileOptions): Unit = {
    when(notChaos && timeSinceReset >= n.U) {
      block(Delay(value, n))
    }
  }
  //
  //  def freeze[T <: Data](value: T, n: Int)(block: T => Any)
  //                       (implicit sourceInfo: SourceInfo,
  //                        compileOptions: CompileOptions): Unit = {
  //    past(value, n)(block)
  //  }

  def initialReg(w: Int, v: Int): InitialReg = {
    val reg = Module(new InitialReg(w, v))
    reg
  }

  def anyconst(w: Int): UInt = {
    val cst = Module(new AnyConst(w))
    cst.io.out
  }


}
