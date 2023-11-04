package Gcd

import chipsalliance.rocketchip.config.Parameters
import chisel3._
import chisel3.internal.sourceinfo.SourceInfo
import chisel3.stage.ChiselStage
import chisel3.util.random.FibonacciLFSR
import freechips.rocketchip.diplomacy._


class ParamBundle(val w: Int) extends Bundle {
  val number = UInt(w.W)
  val start = Bool()
  val done = Bool()
}

case class UpwardParam(width: Int)

case class DownwardParam(width: Int)

case class EdgeParam(width: Int)

object GcdNodeImp extends SimpleNodeImp[DownwardParam, UpwardParam, EdgeParam, ParamBundle] {
  override def edge(pd: DownwardParam, pu: UpwardParam, p: Parameters, sourceInfo: SourceInfo): EdgeParam = {
    EdgeParam(math.max(pd.width, pu.width))
  }

  override def bundle(e: EdgeParam) = new ParamBundle(e.width)

  override def render(e: EdgeParam) = RenderedEdge("blue", s"width = ${e.width}")
}

object GcdDriverNodeImp extends SimpleNodeImp[DownwardParam, UpwardParam, EdgeParam, UInt] {
  override def edge(pd: DownwardParam, pu: UpwardParam, p: Parameters, sourceInfo: SourceInfo): EdgeParam = {
    EdgeParam(math.max(pd.width, pu.width))
  }

  override def bundle(e: EdgeParam): UInt = UInt(e.width.W)

  override def render(e: EdgeParam): RenderedEdge = RenderedEdge("red", s"width = ${e.width}")
}


class GcdDriverNode(widths: Seq[DownwardParam])(implicit valName: ValName)
  extends SourceNode(GcdDriverNodeImp)(widths)

class GcdTestInputNode(width: UpwardParam)(implicit valName: ValName)
  extends SinkNode(GcdDriverNodeImp)(Seq(width))

class GcdTestResultNode(width: UpwardParam)(implicit valName: ValName)
  extends SinkNode(GcdNodeImp)(Seq(width))

class GcdNode(dFn: Seq[DownwardParam] => DownwardParam,
              uFn: Seq[UpwardParam] => UpwardParam)
             (implicit valName: ValName)
  extends MixedNexusNode(GcdDriverNodeImp, GcdNodeImp)(dFn, uFn)

class GcdDriver(width: Int, numOutputs: Int)(implicit p: Parameters) extends LazyModule {
  val node = new GcdDriverNode(Seq.fill(numOutputs)(DownwardParam(width)))

  lazy val module = new LazyModuleImp(this) {
    val w = node.edges.out.head.width
    val io = IO(new Bundle() {
      val inputs = Input(UInt(w.W))
    })
    node.out.foreach {
      case (num, _) =>
        num := FibonacciLFSR.maxPeriod(w)
    }
  }

  override lazy val desiredName = "GcdDriver"
}

class Gcd(implicit p: Parameters) extends LazyModule {
  val node = new GcdNode({
    down: Seq[DownwardParam] =>
      require(down.forall(dp => dp.width == down.head.width))
      down.head
  }, {
    ups: Seq[UpwardParam] =>
      require(ups.forall(up => up.width == ups.head.width))
      ups.head
  })
  lazy val module: LazyModuleImp = new LazyModuleImp(this) {
    val size = node.in.size
    require(size >= 2)
    val width = node.edges.out.head.width
    val start = RegInit(true.B)
    val numbers = RegInit(VecInit(Seq.fill(size)(0.U(width.W))))
    val busy = RegInit(false.B)
    val load = Wire(Bool())
    val done = WireDefault(false.B)
    val minNum = numbers.reduce((x, y) => Mux(x > y, x, y))

    load := start && !busy
    when(!busy) {
      when(start && !load) {
        busy := true.B
      }
    }.otherwise {
      when(done) {
        busy := false.B
      }
    }
    done := busy && numbers.map(_ === minNum).reduce(_ && _)
    when(load) {
      numbers.zip(node.in.map(_._1)).foreach { case (num, input) => num := input }
    }.elsewhen(busy && !done) {
      for (i <- numbers.indices) {
        when(numbers(i) % minNum === 0.U) {
          numbers(i) := minNum
        }.otherwise {
          numbers(i) := numbers(i) % minNum
        }
      }
    }
    node.out.head._1.done := done
    node.out.head._1.start := start
    node.out.head._1.number := minNum
  }

  override lazy val desiredName = "Gcd"
}

class GcdTest(width: Int, numbers: Int)(implicit p: Parameters) extends LazyModule {
  val nodeInput = Seq.fill(numbers)(new GcdTestInputNode(UpwardParam(width)))
  val nodeResult = new GcdTestResultNode(UpwardParam(width))

  lazy val module = new LazyModuleImp(this) {
    val io = IO(new Bundle {
      val error = Output(Bool())
    })
    io.error :=
      nodeResult.in.head._1.done &&
        nodeInput.map(_.in.head._1 % nodeResult.in.head._1.number =/= 0.U).reduce(_ || _)
  }

  override lazy val desiredName = "GcdTest"
}

class GcdTestHarness()(implicit p: Parameters) extends LazyModule {
  val numbers_size = 10
  val gcd = LazyModule(new Gcd())
  val drivers = Seq.fill(numbers_size) {
    LazyModule(new GcdDriver(8, 2))
  }
  val test = LazyModule(new GcdTest(4, numbers_size))
  drivers.foreach {
    driver => gcd.node := driver.node
  }
  drivers.zip(test.nodeInput).foreach {
    case (driver, testNode) => testNode := driver.node
  }
  test.nodeResult := gcd.node

  lazy val module = new LazyModuleImp(this) {
    when(test.module.io.error) {
      printf("something went wrong")
    }
  }

  override lazy val desiredName = "GcdTestHarness"
}


object Main extends App {
  println("DiplomacyGcd")
  (new ChiselStage).emitVerilog(
    LazyModule(new GcdTestHarness()(Parameters.empty)).module,
    Array("--target-dir", "generated/Gcd"))
}