package chiselFv

import chisel3._

class InitialReg(w: Int, value: Int) extends Module {
  val io = IO(new Bundle {
    val in = Input(UInt(w.W))
    val out = Output(UInt(w.W))
  })

  val reg_ = RegInit(value.asUInt(w.W))
  io.out := reg_
  when(reset.asBool) {
    reg_ := value.asUInt(w.W)
  }.otherwise {
    reg_ := io.in
  }
}
