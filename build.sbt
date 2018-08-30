enablePlugins(GitVersioning, GitBranchPrompt, BuildInfoPlugin,JavaAppPackaging)

name := "cassandra-javac-plugin"

Seq(MaxtropyngBuild.common:_*)

libraryDependencies ++= Dependencies.cassandrajavacplugin ++ Seq(
  "org.projectlombok" % "lombok" % "1.18.2",
  "org.scala-lang" % "scala-library" % scalaVersion.value % Test
)

javaHome in Compile := {
  Some(file(sys.props("java.home")).getParentFile)
}


unmanagedJars in Compile ~= { uj =>

	uj :+ Attributed.blank(file(System.getProperty("java.home").dropRight(3) + "lib/tools.jar" ))
}

autoCompilerPlugins := true

javacOptions in Test ++= Seq(
  "-Xplugin:CassandraJavacPlugin"
)
