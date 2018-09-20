import ReleaseTransformations._

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

val circeVersion = "0.10.0-M1"
val http4sVersion = "0.19.0-M2"

val metrics =
  pro("metrics")
    .settings(
      libraryDependencies ++= List(
        "org.log4s" %% "log4s" % "1.6.1",
        "org.typelevel" %% "cats-core" % "1.1.0",
        "org.typelevel" %% "cats-free" % "1.1.0",
        "org.typelevel" %% "cats-effect" % "1.0.0",
      )
    )

val codahale =
  pro("codahale")
    .dependsOn(metrics)
    .settings(
      libraryDependencies ++= List(
        "io.dropwizard.metrics" % "metrics-core" % "3.1.2",
      )
    )

val request =
  pro("request")
    .dependsOn(metrics)
    .settings(
      libraryDependencies ++= List(
        "io.circe" %% "circe-parser" % circeVersion,
      )
    )

val play =
  pro("play")
    .dependsOn(request, codahale)
    .settings(
      libraryDependencies ++= List(
        "com.typesafe.play" %% "play" % "2.6.12",
        "com.typesafe.play" %% "play-guice" % "2.6.12",
        "com.typesafe.play" %% "play-ahc-ws" % "2.6.12",
        "com.typesafe.play" %% "play-specs2" % "2.6.12" % "test",
        "de.leanovate.play-mockws" %% "play-mockws" % "2.6.2" % "test",
      )
    )

val http4s =
  pro("http4s")
    .dependsOn(request, codahale)
    .settings(
      libraryDependencies ++= List(
        "org.http4s" %% "http4s-core" % http4sVersion,
        "org.http4s" %% "http4s-blaze-client" % http4sVersion,
        "org.http4s" %% "http4s-dsl" % http4sVersion % "test",
        "org.http4s" %% "http4s-blaze-server" % http4sVersion % "test",
        "org.http4s" %% "http4s-circe" % http4sVersion % "test",
        "io.circe" %% "circe-generic" % circeVersion,
      )
    )

val integration =
  pro("integration")
    .dependsOn(request, codahale)
    .settings(
      publishTo := None,
      publish := (()),
      publishLocal := (()),
    )
    .settings(noPublish)

val root =
  basicProject(project.in(file(".")))
    .aggregate(metrics, request, codahale, play, http4s, integration)
    .settings(noPublish)
