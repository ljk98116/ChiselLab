package build

import mill._
import mill.scalalib._
import mill.util.bsp._
import mill.scalalib.scalafmt.ScalafmtModule
import coursier.core.Repository
import mill.api.Task.Simple

// used for metals debugging
object MyCPU extends ScalaModule with ScalafmtModule { m =>
    def scalaVersion = "2.13.18"
    override def scalacOptions = Seq(
        "-language:reflectiveCalls",
        "-deprecation",
        "-feature",
        "-Xcheckinit"
    )
    override def mvnDeps = Seq(mvn"org.chipsalliance::chisel:7.7.0")
    override def scalacPluginMvnDeps = Seq(mvn"org.chipsalliance:::chisel-plugin:7.7.0")

    object test extends ScalaTests with TestModule.ScalaTest with ScalafmtModule {
        override def mvnDeps = m.mvnDeps() ++ Seq(
            mvn"org.scalatest::scalatest::3.2.19",
            // for formal flow in future
            mvn"edu.berkeley.cs::chiseltest:6.0.0"
        )
    }

    override def repositoriesTask = Task.Anon {
        Seq(
            coursier.MavenRepository("https://repo.scala-sbt.org/scalasbt/maven-releases"),
            coursier.MavenRepository("https://oss.sonatype.org/content/repositories/releases"),
            coursier.MavenRepository("https://oss.sonatype.org/content/repositories/snapshots")
        ) ++ super.repositoriesTask()
    }
}