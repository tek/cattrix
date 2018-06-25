import sbt._, Keys._

object Util
extends AutoPlugin
{
  object autoImport
  {
    val catsVersion = settingKey[String]("cats version")

    def testDeps = libraryDependencies ++= List(
      "org.specs2" %% "specs2-core" % "4.1.0" % "test"
    )

    def pro(n: String) =
      Project(n, file(n))
      .settings(
        name := s"cats-http-metrics-$n",
        addCompilerPlugin("org.scalameta" % "paradise" % "3.0.0-M10" cross CrossVersion.full),
        addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.7"),
        resolvers += Resolver.sonatypeRepo("releases"),
        scalacOptions ++= List(
          "-feature",
          "-deprecation",
          "-unchecked",
          "-Ypartial-unification",
          "-language:higherKinds",
          "-language:implicitConversions",
        ),
        fork := true,
      )
      .settings(testDeps)
  }
}
