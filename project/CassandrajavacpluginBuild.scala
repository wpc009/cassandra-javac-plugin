import sbt._
import sbt.Keys._

object CassandrajavacpluginBuild extends Build {


  val commonSettings = Seq[Def.Setting[_]](
    sourceManaged :=  { baseDirectory.value / "src_managed" },
    organization := Conf.org,
    scalaVersion := "2.12.6",
    scalacOptions ++= Seq(
        "-deprecation",
        "-feature",
        "-language:postfixOps"
    )
  )

  lazy val testSettings = Seq[Def.Setting[_]](

  )

  lazy val publish_settings = Seq(
       publishTo := {
      if(isSnapshot.value)
        Some("snapshots" at "http://artifactory.segmetics.com/artifactory/libs-snapshot-local")
      else
        Some("artifactory.segmetics.com-releases" at "http://artifactory.segmetics.com/artifactory/libs-release-local")
    },
    credentials += Credentials("Artifactory Realm","artifactory.segmetics.com","<username>","<password>")
  )


  lazy val cassandrajavacplugin = Project(
    id = "cassandra-javac-plugin",
    base = file("."),
    settings = Defaults.coreDefaultSettings ++ commonSettings ++ Seq(
      name := "cassandra-javac-plugin",
      libraryDependencies ++= Dependencies.cassandrajavacplugin
      // add other settings here
    )
  )


}

object DepVersions {
  lazy val akka = "2.3.10"

}

object Conf {


  lazy val org = "com.maxtropy"

}

object Dependencies {

  private[this] lazy val logging = Seq(
    "ch.qos.logback" % "logback-classic" % "1.1.3"
  )

  private[this] lazy val unitest = Seq(
    "org.scalatest" %% "scalatest" % "3.0.5" % Test
  )

  lazy val cassandrajavacplugin = Seq(
  ) ++ unitest ++ logging
}

case class Ver(version:String){

  override def toString(): String =  version 

  def snapshot():String = version + "-SNAPSHOT"

def beta(v:Int):String = "%s-BetaV%d".format(version,v)

  def release():String = version


}



