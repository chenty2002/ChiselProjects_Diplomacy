package Adder_Tutorial

import Chisel.Flipped
import org.chipsalliance.cde.config.Parameters
import chisel3._
import chisel3.experimental.IO
import chisel3.util._
import chisel3.internal.sourceinfo.SourceInfo
import chisel3.stage.ChiselStage
import chisel3.util.random.FibonacciLFSR
import freechips.rocketchip.diplomacy.{LazyModule, LazyModuleImp, NexusNode, RenderedEdge, SimpleNodeImp, SinkNode, SourceNode, ValName}
import chiselFv._
import utility.FileRegisters


case class UpwardParam(width: Int)

case class DownwardParam(width: Int)

case class EdgeParam(width: Int)

// PARAMETER TYPES:                       D              U            E          B
object NodeImp extends SimpleNodeImp[DownwardParam, UpwardParam, EdgeParam, UInt] {
  def edge(pd: DownwardParam, pu: UpwardParam, p: Parameters, sourceInfo: SourceInfo) = {
    if (pd.width < pu.width) EdgeParam(pd.width) else EdgeParam(pu.width)
  }

  def bundle(e: EdgeParam) = UInt(e.width.W)

  def render(e: EdgeParam) = RenderedEdge("blue", s"width = ${e.width}")
}

/** node for [[Driver]] (source) */
class DriverNode(widths: Seq[DownwardParam])(implicit valName: ValName)
  extends SourceNode(NodeImp)(widths)

/** node for [[Checker]] (sink) */
class CheckerNode(width: UpwardParam)(implicit valName: ValName)
  extends SinkNode(NodeImp)(Seq(width))

/** node for [[Adder]] (nexus) */
class AdderNode(dFn: Seq[DownwardParam] => DownwardParam,
                uFn: Seq[UpwardParam] => UpwardParam)(implicit valName: ValName)
  extends NexusNode(NodeImp)(dFn, uFn)


/** adder DUT (nexus) */
class Adder(implicit p: Parameters) extends LazyModule {
  val node = new AdderNode(
    { dps: Seq[DownwardParam] =>
      require(dps.forall(dp => dp.width == dps.head.width), "inward, downward adder widths must be equivalent")
      dps.head
    },
    { ups: Seq[UpwardParam] =>
      require(ups.forall(up => up.width == ups.head.width), "outward, upward adder widths must be equivalent")
      ups.head
    }
  )
  class AdderModuleImp(wrapper: LazyModule) extends LazyModuleImp(wrapper) {
    require(node.in.size >= 2)
    node.out.foreach(_._1 := node.in.map(_._1).reduce(_ + _))
  }
  
  lazy val module = new AdderModuleImp(this) 

  override lazy val desiredName = "Adder"
}


/** driver (source)
 * drives one random number on multiple outputs */
class Driver(width: Int, numOutputs: Int)(implicit p: Parameters) extends LazyModule {
  val node = new DriverNode(Seq.fill(numOutputs)(DownwardParam(width)))
  val output = new DriverNode(Seq(DownwardParam(width)))

  class DriverModuleImp(wrapper: LazyModule) extends LazyModuleImp(wrapper) {
    // check that node parameters converge after negotiation
    val negotiatedWidths = node.edges.out.map(_.width)
    require(negotiatedWidths.forall(_ == negotiatedWidths.head), "outputs must all have agreed on same width")
    val finalWidth = negotiatedWidths.head

    // generate random addend (notice the use of the negotiated width)
    val randomAddend = FibonacciLFSR.maxPeriod(finalWidth)

    // drive signals
    node.out.foreach { case (addend, _) => addend := randomAddend }
  }
  
  lazy val module = new DriverModuleImp(this) 

  override lazy val desiredName = "Driver"
}


/** checker (sink) */
class Checker(width: Int, numOperands: Int)(implicit p: Parameters) extends LazyModule {
  val numberFromDrivers = Seq.fill(numOperands) {
    new CheckerNode(UpwardParam(width))
  }
  val numberFromAdder = new CheckerNode(UpwardParam(width))

  class CheckerModuleImp(wrapper: LazyModule) extends LazyModuleImp(wrapper) with Formal {
    val io = IO(new Bundle {
      val error = Output(Bool())
    })

    // print operation
    printf(numberFromDrivers.map(node => p"${node.in.head._1}").reduce(_ + p" + " + _) + p" = ${numberFromAdder.in.head._1}")
    // basic correctness checking
    assert(numberFromAdder.in.head._1 === numberFromDrivers.map(_.in.head._1).reduce(_ + _))
    io.error := numberFromAdder.in.head._1 =/= numberFromDrivers.map(_.in.head._1).reduce(_ + _)
  }

  lazy val module = new CheckerModuleImp(this)

  override lazy val desiredName = "Checker"
}


/** top-level connector */
class AdderTestHarness()(implicit p: Parameters) extends LazyModule {
  val numOperands = 2
  val adder = LazyModule(new Adder)
  // 8 will be the downward-traveling widths from our drivers
  val drivers = Seq.fill(numOperands) {
    LazyModule(new Driver(width = 8, numOutputs = 2))
  }
  // 4 will be the upward-traveling width from our checker
  val checker = LazyModule(new Checker(width = 4, numOperands = numOperands))

  // create edges via binding operators between nodes in order to define a complete graph
  drivers.foreach {
    driver =>
      adder.node := driver.node
  }

  drivers.zip(checker.numberFromDrivers).foreach {
    case (driver, checkerNode) =>
      checkerNode := driver.node
  }
  checker.numberFromAdder := adder.node

  class HarnessModuleImp(wrapper: LazyModule) extends LazyModuleImp(wrapper) {
    when(checker.module.io.error) {
      printf("something went wrong")
    }
  }

  lazy val module = new HarnessModuleImp(this) 

  override lazy val desiredName = "AdderTestHarness"
}

object Main extends App {
  println("Generating Adder.AdderDiplomacy hardware")
  val harness = LazyModule(new AdderTestHarness()(Parameters.empty))
  FileRegisters.writeOutputFile("generated/Adder_Tutorial", "Adder_Tutorial.v", (new ChiselStage).emitVerilog(
    harness.module
  ))
  Check.bmc(() => LazyModule(new AdderTestHarness()(Parameters.empty)), depth=50)
}