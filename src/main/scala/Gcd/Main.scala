package Gcd

import chipsalliance.rocketchip.config.Parameters
import chisel3._
import chisel3.internal.sourceinfo.SourceInfo
import chisel3.stage.ChiselStage
import freechips.rocketchip.diplomacy.{LazyModule, LazyModuleImp, NexusNode, RenderedEdge, SimpleNodeImp, SinkNode, SourceNode, ValName}

import scala.util.Random


class ParamBundle(val w: Int) extends Bundle {
  val number = UInt(w.W)
  val start = Bool()
  val done = Bool()
}

case class UpwardParam(width: Int)

case class DownwardParam(width: Int)

case class EdgeParam(width: Int)

object GcdNodeImp extends SimpleNodeImp[DownwardParam, UpwardParam, EdgeParam, ParamBundle] {
  def edge(down: DownwardParam, up: UpwardParam, p: Parameters, sourceInfo: SourceInfo): EdgeParam = {
    EdgeParam(math.max(down.width, up.width))
  }

  def bundle(edge: EdgeParam) = new ParamBundle(edge.width)

  def render(edge: EdgeParam) = RenderedEdge("blue", s"width = ${edge.width}")
}


class GcdDriverNode(widths: Seq[DownwardParam])(implicit valName: ValName)
  extends SourceNode(GcdNodeImp)(widths)

class GcdTestNode(width: UpwardParam)(implicit valName: ValName)
  extends SinkNode(GcdNodeImp)(Seq(width))

class GcdNode(dFn: Seq[DownwardParam] => DownwardParam,
              uFn: Seq[UpwardParam] => UpwardParam)(implicit valName: ValName)
  extends NexusNode(GcdNodeImp)(dFn, uFn)


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
    require(node.in.size >= 2)

    val size = node.in.size
    val width = node.in.head._2.width
    val start = node.in.head._1.start
    val numbers = RegInit(VecInit(Seq.fill(size)(0.U(width.W))))
    val busy = RegInit(false.B)
    val load = Wire(Bool())
    val done = Wire(Bool())
    val minNum = numbers.reduce((x, y) => Mux(x > y, x, y))

    load := start && !busy
    when(!busy) {
      when(start) {
        busy := true.B
      }
    }.otherwise {
      when(done) {
        busy := false.B
      }
    }
    done := busy && numbers.map(_ === minNum).reduce(_ && _)

    when(load) {
      for (i <- numbers.indices) {
        numbers(i) := node.in(i)._1.number
      }
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

class GcdDriver(width: Int, numOutputs: Int)(implicit p: Parameters) extends LazyModule {
  val node = new GcdDriverNode(Seq.fill(numOutputs)(DownwardParam(width)))

  lazy val module = new LazyModuleImp(this) {
    val widths = node.edges.out.map(_.width)
    val w = widths.head
    node.out.foreach {
      case (num, _) =>
        num.start := true.B
        num.number := (Random.nextInt() % (1 << w - 1) + 1).asUInt(w.W)
        num.done := false.B
    }
  }

  override lazy val desiredName = "GcdDriver"
}

class GcdTest(width: Int, numbers: Int)(implicit p: Parameters) extends LazyModule {
  val nodeSeq = Seq.fill(numbers)(new GcdTestNode(UpwardParam(width)))
  val nodeGcd = new GcdTestNode(UpwardParam(width))

  lazy val module = new LazyModuleImp(this) {
    val io = IO(new Bundle {
      val error = Output(Bool())
    })
    io.error := nodeGcd.in.head._1.done &&
      nodeSeq.map(_.in.head._1.number % nodeGcd.in.head._1.number =/= 0.U).reduce(_ || _)
  }

  override lazy val desiredName = "GcdTest"
}

class GcdTestHarness()(implicit p: Parameters) extends LazyModule {
  val numbers_size = 10
  val gcd = LazyModule(new Gcd)
  val drivers = Seq.fill(numbers_size) {
    LazyModule(new GcdDriver(8, 2))
  }
  val test = LazyModule(new GcdTest(4, numbers_size))
  drivers.foreach {
    driver => gcd.node := driver.node
  }
  drivers.zip(test.nodeSeq).foreach {
    case (driver, testNode) => testNode := driver.node
  }
  test.nodeGcd := gcd.node

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
    Array("--target-dir", "generated"))
}