import mill._
import scalalib._

trait DiplomacyModule extends ScalaModule {

  def rocketModule: ScalaModule

  override def moduleDeps = super.moduleDeps ++ Seq(rocketModule)
}
