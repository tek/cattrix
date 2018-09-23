import sbt._, Keys._

object Util
extends AutoPlugin
{
  object autoImport
  {
    def testDeps = libraryDependencies ++= List(
      "org.specs2" %% "specs2-core" % "4.1.0" % "test"
    )

    val github = "https://github.com/tek"
    val projectName = "cattrix"
    val repo = s"$github/$projectName"

    def noPublish: List[Setting[_]] = List(skip in publish := true)

    def basicProject(pro: Project): Project =
      pro.settings(
        scalaVersion := "2.12.4",
        organization := "io.tryp",
        resolvers += Resolver.sonatypeRepo("releases"),
        scalacOptions ++= List(
          "-feature",
          "-deprecation",
          "-unchecked",
          "-Ypartial-unification",
          "-language:higherKinds",
          "-language:implicitConversions",
          "-Ywarn-value-discard",
          "-Ywarn-unused:imports",
          "-Ywarn-unused:implicits",
          "-Ywarn-unused:params",
          "-Ywarn-unused:patvars",
        ),
        fork := true,
      )

    def pro(n: String) =
      basicProject(Project(n, file(n)))
      .settings(
        name := s"$projectName-$n",
        addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.7"),
        publishMavenStyle := true,
        publishTo := Some(
          if (isSnapshot.value) Opts.resolver.sonatypeSnapshots
          else Resolver.url("sonatype staging", url("https://oss.sonatype.org/service/local/staging/deploy/maven2"))
        ),
        licenses := List("MIT" -> url("http://opensource.org/licenses/MIT")),
        homepage := Some(url(repo)),
        scmInfo := Some(ScmInfo(url(repo), s"scm:git@github.com:tek/$projectName")),
        developers := List(Developer(id="tek", name="Torsten Schmits", email="torstenschmits@gmail.com",
          url=url(github))),
      )
      .settings(testDeps)
  }
}
