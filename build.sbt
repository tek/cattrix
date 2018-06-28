import ReleaseTransformations._

name := "cats-http-metrics"
publish := (())
publishLocal := (())
releaseCrossBuild := true
releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  setReleaseVersion,
  releaseStepCommandAndRemaining("+publishSigned"),
  releaseStepCommand("sonatypeReleaseAll"),
  commitReleaseVersion,
  tagRelease,
  setNextVersion,
  commitNextVersion
)

val metrics = pro("metrics")
  .settings(
    libraryDependencies ++= List(
      "org.log4s" %% "log4s" % "1.6.1",
      "org.typelevel" %% "cats-core" % "1.1.0",
      "org.typelevel" %% "cats-free" % "1.1.0",
      "org.typelevel" %% "cats-effect" % "1.0.0-RC2",
      "io.dropwizard.metrics" % "metrics-core" % "3.1.2",
      "com.github.mpilquist" %% "simulacrum" % "0.12.0",
    )
  )

val request =
  pro("request")
    .dependsOn(metrics)

val play =
  pro("play")
    .dependsOn(request)
    .settings(
      libraryDependencies ++= List(
        "com.typesafe.play" %% "play" % "2.6.12",
        "com.typesafe.play" %% "play-guice" % "2.6.12",
        "com.typesafe.play" %% "play-ahc-ws" % "2.6.12",
        "com.typesafe.play" %% "play-specs2" % "2.6.12" % "test",
        "de.leanovate.play-mockws" %% "play-mockws" % "2.6.2" % "test",
      )
    )

val root =
  basicProject(project.in(file(".")))
    .aggregate(metrics, request, play)
