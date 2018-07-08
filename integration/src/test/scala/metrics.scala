package chm

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
      r <- Metrics.run(() => run)
    } yield r
    val result = steps.foldMap(NoMetrics.interpreter[IO]).unsafeRunSync
    result === 5
  }

  def http = {
    val payload = "hello"
    val m = Codahale.as[IO]("io.tryp")
    val sh = PureHttp.partial[IO] { case a => IO.pure(Response.ok(payload)) }
    val http = Http.fromConfig[IO, PureHttp[?[_], Request, Response], Codahale[IO], Request, Response](
      HttpConfig[IO, PureHttp[?[_], Request, Response], Codahale[IO]](sh, m)
    )
    val io: IO[Response] = http.get("http://tryp.io", "tryp")
    io.unsafeRunSync must_== Response(200, payload, Nil, Nil)
  }
}
