import com.typesafe.sbt.SbtGit.{GitKeys, git}
import com.typesafe.sbt.git.DefaultReadableGit
import sbtbuildinfo.BuildInfoPlugin.BuildInfoKey
import sbtbuildinfo.{BuildInfoKeys, BuildInfoOption, BuildInfoPlugin}

import com.typesafe.sbt._
import sbt.Keys._
import sbt._


object MaxtropyngBuild {


  def proj(name: String, base: File) = Project(id = name, base = base).enablePlugins(GitVersioning, GitBranchPrompt, BuildInfoPlugin)

  val scalastyleReport = taskKey[File]("create a report from Scalastyle")


  val test2 = taskKey[String]("test task")


  val dev = config("dev") extend Test

  val release = config("release") extend Compile

  val view = TaskKey[Unit]("view", "view settings")
  val os = SettingKey[OS]("os", "")




  lazy val common = Seq(
    organization := Conf.org,
    //    target := (baseDirectory in ThisBuild).value / "target" / name.value,
    scalacOptions ++= Seq(
      "-feature",
      "-deprecation",
      "-language:postfixOps"
    ),
    scalaVersion in ThisBuild := "2.12.6",
    exportJars := true,
    resolvers ++= Seq(
      "Snapshot" at "http://artifactory.segmetics.com/artifactory/libs-snapshot-local/",
      "Release" at "http://artifactory.segmetics.com/artifactory/libs-releases-local/",
      "Typesafe Releases" at "http://repo.typesafe.com/typesafe/maven-releases/"
    ),
    compileOrder := CompileOrder.Mixed,
    credentials += Credentials(Path.userHome / ".ivy2" / ".credentials"),
    git.useGitDescribe := true,
    GitKeys.gitReader in ThisProject :=   { new DefaultReadableGit(baseDirectory.value) },
    GitKeys.gitUncommittedChanges in ThisProject := {(GitKeys.gitReader in ThisProject).value.withGit( _ .hasUncommittedChanges) },
    sourceManaged := { (baseDirectory in ThisProject).value / "src_managed" },
    BuildInfoKeys.buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion, BuildInfoKeys.buildInfoBuildNumber),
    BuildInfoKeys.buildInfoOptions += BuildInfoOption.BuildTime,
    BuildInfoKeys.buildInfoPackage := organization.value + ".buildinfo." + name.value.replaceAll("[-_.]", ""),
    /**
    * Sbt resolve cache. see [#http://www.scala-sbt.org/0.13/docs/Cached-Resolution.html]
    */
    updateOptions := updateOptions.value.withCachedResolution(true),
    os := OS()
  )




  /**
    * Projects end
    */



  object Conf {
    lazy val org = "com.maxtropy"
    lazy val ver = "0.0.1"
  }





  object OS {

    object Name extends Enumeration {
      type Name = Value
      val Windows, Linux, Mac = Value
    }

    object Arch extends Enumeration {
      type Arch = Value
      val X86, X86_64, AMD64, PPC = Value
    }

    def apply(): OS = {
      val name = sys.props.get("os.name").flatMap(n =>
        OS.Name.values.find(v => n.startsWith(v.toString))
      ).getOrElse(sys.error("Unknown OS name!"))

      val arch = sys.props.get("os.arch").flatMap(a =>
        OS.Arch.values.find(_.toString.equalsIgnoreCase(a))
      ).getOrElse(sys.error("Unknown OS arch!"))

      OS(name, arch)
    }

  }

  case class OS(name: OS.Name.Name, arch: OS.Arch.Arch)


}

