publishTo := {
	if (isSnapshot.value)
        Some("snapshots" at "http://artifactory.segmetics.com/artifactory/libs-snapshot-local")
      else
        Some("artifactory.segmetics.com-releases" at "http://artifactory.segmetics.com/artifactory/libs-release-local")
    }

credentials in ThisBuild ++= Seq(
	Some(Path.userHome / ".ivy2" / ".credentials"),
	sys.env.get("SBT_CREDENTIALS").map(new File(_)),
	sys.props.get("sbt.boot.credentials").map(new File(_))
).filter(_.isDefined).flatten.map{ file => Credentials(file) }
  
