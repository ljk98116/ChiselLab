import bloop.config.Config.Scala
import mill._
import mill.scalalib._
import mill.scalalib.scalafmt.ScalafmtModule
import coursier.core.Repository
import $ivy.`com.lihaoyi::mill-contrib-bloop:`
import mill.bsp._

object MyCPU extends ScalaModule with ScalafmtModule { m =>
    def scalaVersion = "2.12.18"
    override def scalacOptions = Seq(
        "-language:reflectiveCalls",
        "-deprecation",
        "-feature",
        "-Xcheckinit"
    )
    override def ivyDeps = Agg(
        mvn"org.chipsalliance::chisel:5.3.0",
        mvn"edu.berkeley.cs::rocketchip:1.2.6",
        mvn"org.chipsalliance::firtool-resolver:2.0.1",
        // mvn"org.chipsalliance:llvm-firtool:1.140.0" useless
    )
    override def scalacPluginIvyDeps = Agg(mvn"org.chipsalliance:::chisel-plugin:5.3.0")
}