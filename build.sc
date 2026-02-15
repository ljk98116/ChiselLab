import os.Path
import mill._
import mill.scalalib._
import mill.scalalib.scalafmt.ScalafmtModule
import coursier.core.Repository
import $ivy.`com.lihaoyi::mill-contrib-bloop:`
import mill.bsp._
import publish._
import $ivy.`de.tototec::de.tobiasroeser.mill.vcs.version::0.4.0`
import de.tobiasroeser.mill.vcs.version.VcsVersion

import $file.tools.cde.common
import $file.tools.diplomacy.common
import $file.tools.chisel.build

object v {
  val scala = "2.13.12"
  val chiselCrossVersions = Map(
    "6.7.0" -> (ivy"org.chipsalliance::chisel:6.7.0", ivy"org.chipsalliance:::chisel-plugin:6.7.0"),
    // build from project from source
    "source" -> (ivy"org.chipsalliance::chisel:99", ivy"org.chipsalliance:::chisel-plugin:99"),
  )
  val sourcecode = ivy"com.lihaoyi::sourcecode:0.3.1"
}

object cde extends CDE

trait CDE extends millbuild.tools.cde.common.CDEModule with DiplomacyPublishModule with ScalaModule {
  def scalaVersion: T[String] = T(v.scala)

  override def millSourcePath = os.pwd / "tools" / "cde" / "cde"
}

object chisel extends Chisel

trait Chisel
  extends millbuild.tools.chisel.build.Chisel {
  def crossValue = v.scala
  override def millSourcePath = os.pwd / "tools" / "chisel"
  def scalaVersion = T(v.scala)
}

object diplomacy extends Cross[Diplomacy](v.chiselCrossVersions.keys.toSeq)

trait Diplomacy
    extends millbuild.tools.diplomacy.common.DiplomacyModule
    with DiplomacyPublishModule
    with ScalafmtModule
    with Cross.Module[String] {

  def scalaVersion: T[String] = T(v.scala)
  def millSourcePath = os.pwd / "tools" / "diplomacy"

  def chiselModule = Option.when(crossValue == "source")(chisel)
  def chiselPluginJar = T(Option.when(crossValue == "source")(chisel.pluginModule.jar()))
  def chiselIvy = Option.when(crossValue != "source")(v.chiselCrossVersions(crossValue)._1)
  def chiselPluginIvy = Option.when(crossValue != "source")(v.chiselCrossVersions(crossValue)._2)
  def cdeModule = cde
  def sourcecodeIvy = v.sourcecode
}

trait DiplomacyPublishModule extends PublishModule {
  def publishVersion = de.tobiasroeser.mill.vcs.version.VcsVersion.vcsState().format()
  def pomSettings = PomSettings(
    description = artifactName(),
    organization = "org.chipsalliance",
    url = "https://www.github.com/chipsalliance/diplomacy",
    licenses = Seq(License.`Apache-2.0`),
    versionControl = VersionControl.github("chipsalliance", "diplomacy"),
    developers = Seq(
      Developer("terpstra", "Wesley W. Terpstra", "https://github.com/terpstra"),
      Developer("hcook", "Henry Cook", "https://github.com/hcook"),
      Developer("sequencer", "Jiuyang Liu", "https://github.com/sequencer")
    )
  )
}

object MyCPU extends ScalaModule with ScalafmtModule { m =>
    def scalaVersion = "2.13.12"
    override def scalacOptions = Seq(
        "-language:reflectiveCalls",
        "-deprecation",
        "-feature",
        "-Xcheckinit"
    )
    override def moduleDeps: Seq[JavaModule] = Seq(
      cde, chisel
    )
    override def sources: T[Seq[PathRef]] = T{
      Seq(
        PathRef(os.pwd / "tools" / "cde" / "cde" / "src"),
        PathRef(os.pwd / "tools" / "diplomacy" / "diplomacy" / "src"),
      ) ++ super.sources().toSeq
    }
}