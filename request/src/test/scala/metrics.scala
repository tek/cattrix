package chm

import cats.Eval
import cats.data.Kleisli
import cats.syntax.all._
import cats.effect.IO
import org.specs2.Specification

class MetricsSpec
extends Specification
{
  def is = s2"""
  compose metrics actions $compose
  run an http request $http
  """

  def run: IO[Int] = IO.pure(5)

  def compose = {
    val steps = for {
      _ <- Metrics.incCounter[IO]("active")
      r <- MetricsEval.run(Eval.now(run))
    } yield r
    val result = MetricsEval.io(NoMetrics())(steps).unsafeRunSync
    result === 5
  }

  def http = {
    val payload = "hello"
    val m = Codahale.as[IO]("io.tryp")
    val sh = PureHttp.partial[IO] { case a => IO.pure(Right(Response.ok(payload))) }
    val http = Http.fromConfig(HttpConfig(sh, m))
    val io: IO[Either[String, Response]] = http.get("http://tryp.io", "tryp")
    io.unsafeRunSync.must(beRight(Response(200, payload, Nil, Nil)))
  }
}