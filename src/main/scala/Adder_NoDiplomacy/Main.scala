package Adder_NoDiplomacy

import chisel3._
import chisel3.experimental.IO
import chisel3.util._
import chisel3.internal.sourceinfo.SourceInfo
import chisel3.stage.ChiselStage
import chisel3.util.random.FibonacciLFSR
import chiselFv._

/** adder DUT (nexus) */
class Adder extends Module {
    val io = IO(new Bundle {
        val driver1_in = Input(UInt(4.W))
        val driver2_in = Input(UInt(4.W))
        val adder_out =  Output(UInt(4.W))
    })
    io.adder_out := io.driver1_in + io.driver2_in
}


/** driver (source)
 * drives one random number on multiple outputs */
class Driver extends Module {
    val io = IO(new Bundle {
        val out = Output(UInt(4.W))
    })
    io.out := FibonacciLFSR.maxPeriod(4)
}


/** checker (sink) */
class Checker extends Module with Formal {
    val io = IO(new Bundle {
        val driver1_in = Input(UInt(4.W))
        val driver2_in = Input(UInt(4.W))
        val adder_in = Input(UInt(4.W))
      val error = Output(Bool())
    })
    assert(io.adder_in === io.driver1_in + io.driver2_in)
    io.error := io.adder_in =/= io.driver1_in + io.driver2_in
//    io.w := numberFromAdder.edges.in.head.width.U
//    io.error := numberFromDrivers.map(_.in.head._1).reduce(_ + _) === 1.U
  
}
class AdderTestHarness extends Module {
    val io = IO(new Bundle {
        val error = Output(Bool())
    })
    val driver1 = Module(new Driver)
    val driver2 = Module(new Driver)
    val adder = Module(new Adder)
    val checker = Module(new Checker)
    adder.io.driver1_in := driver1.io.out
    adder.io.driver2_in := driver2.io.out
    checker.io.driver1_in := driver1.io.out
    checker.io.driver2_in := driver2.io.out
    checker.io.adder_in := adder.io.adder_out
    io.error := checker.io.error
}


object Main extends App {
  println("Generating Adder.Adder_noDiplomacy hardware")
//  val harness = LazyModule(new AdderTestHarness()(Parameters.empty))
//  println()
//  writeOutputFile("generated/Adder_Tutorial", "Adder_Tutorial.v", (new ChiselStage).emitVerilog(
//    harness.module
//  ))
  Check.bmc(() => new AdderTestHarness, depth=50)
//  writeOutputFile("generated/Adder_Tutorial", "Adder_Tutorial.graphml", adder.graphML)
}