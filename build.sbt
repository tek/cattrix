scalaVersion := "2.12.4"
organization := "io.tryp"
name := "cats-http-metrics"
publishMavenStyle := true
publishTo := Some(
  if (isSnapshot.value) Opts.resolver.sonatypeSnapshots
  else Resolver.url("sonatype staging", url("https://oss.sonatype.org/service/local/staging/deploy/maven2"))
)
licenses := List("MIT" -> url("http://opensource.org/licenses/MIT"))
homepage := Some(url("https://github.com/tek/cats-http-metrics"))
scmInfo := Some(ScmInfo(url("https://github.com/tek/cats-http-metrics"), "scm:git@github.com:tek/cats-http-metrics"))
developers := List(Developer(id="tek", name="Torsten Schmits", email="torstenschmits@gmail.com",
  url=url("https://github.com/tek")))

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

val metrics = pro("metrics")
  .settings(
    libraryDependencies ++= List(
      "org.log4s" %% "log4s" % "1.6.1",
      "org.typelevel" %% "cats-core" % "1.1.0",
      "org.typelevel" %% "cats-effect" % "1.0.0-RC2",
      "io.dropwizard.metrics" % "metrics-core" % "3.1.2",
      "com.github.mpilquist" %% "simulacrum" % "0.12.0",
    )
  )

val request = pro("request")
  .dependsOn(metrics)

val play = pro("play")
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

val root = project.in(file(".")).settings(publish := (()), publishLocal := (())).aggregate(metrics, request, play)
