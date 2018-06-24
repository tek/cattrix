scalaVersion := "2.12.4"
organization := "io.tryp"
name := "cats-http-metrics"
fork := true
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

def testDeps = libraryDependencies ++= List(
  "org.specs2" %% "specs2-core" % "4.1.0" % "test"
)

val metrics = pro("metrics")
  .settings(
    libraryDependencies ++= List(
      "org.typelevel" %% "cats-core" % "1.1.0",
      "org.typelevel" %% "cats-effect" % "1.0.0-RC2",
      "io.dropwizard.metrics" % "metrics-core" % "3.1.2",
      "com.github.mpilquist" %% "simulacrum" % "0.12.0",
    )
  )

val request = pro("request")
  .dependsOn(metrics)
  .settings(testDeps)
  .settings(
    libraryDependencies ++= List(
      "org.log4s" %% "log4s" % "1.6.1",
      "com.typesafe.play" %% "play" % "2.6.12",
      "com.typesafe.play" %% "play-ws" % "2.6.12",
      "io.frees" %% "frees-core" % "0.8.2",
    )
  )

val root = project.in(file(".")).settings(publish := (()), publishLocal := (())).aggregate(metrics, request)
