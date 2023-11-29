package chiselFv

import chisel3._

class ResetCounter extends Module {
  val io = IO(new Bundle {
    val timeSinceReset = Output(UInt(32.W))
    val notChaos = Output(Bool())
  })

  val count = RegInit(0.U(32.W))
  val flag = RegInit(false.B)
  io.timeSinceReset := count
  io.notChaos := flag

  when(reset.asBool) {
    count := 0.U
    flag := true.B
  }.elsewhen(flag) {
    count := count + 1.U
  }
}
