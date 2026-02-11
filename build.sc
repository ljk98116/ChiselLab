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

trait CDEPublishModule extends PublishModule {
  def publishVersion = de.tobiasroeser.mill.vcs.version.VcsVersion.vcsState().format()

  def pomSettings = PomSettings(
    description = artifactName(),
    organization = "org.chipsalliance",
    url = "https://www.github.com/chipsalliance/cde",
    licenses = Seq(License.`Apache-2.0`),
    versionControl = VersionControl.github("chipsalliance", "cde"),
    developers = Seq(
      Developer("terpstra", "Wesley W. Terpstra", "https://github.com/terpstra")
    )
  )
  // TODO: wait Chisel has mill-based release flow, let's copy&paste from it.
}

object cde extends CDE

trait CDE
  extends common.CDEModule
    with CDEPublishModule
    with ScalafmtModule {
  override def scalaVersion = "2.13.18"
  override def millSourcePath: Path = os.pwd / "tools" / "cde" / "cde"
  override def sources: T[Seq[PathRef]] = T{
    Seq(PathRef(os.pwd / "tools" / "cde" / "cde" / "src"))
  }
}

object MyCPU extends ScalaModule with ScalafmtModule { m =>
    def scalaVersion = "2.13.18"
    override def scalacOptions = Seq(
        "-language:reflectiveCalls",
        "-deprecation",
        "-feature",
        "-Xcheckinit"
    )
    override def ivyDeps = Agg(
        ivy"org.chipsalliance::chisel:7.7.0"
    )

    override def moduleDeps: Seq[JavaModule] = Seq(cde)
    override def sources: T[Seq[PathRef]] = T{
      Seq(
        PathRef(os.pwd / "tools" / "cde" / "cde" / "src")
      ) ++ super.sources().toSeq
    }    
    override def scalacPluginIvyDeps = Agg(ivy"org.chipsalliance:::chisel-plugin:7.7.0")
}