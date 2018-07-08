package chm

import cats.syntax.all._
import cats.effect.IO
import org.specs2.Specification
import fs2.{Stream, text}
import org.http4s.{Request => HRequest, Response => HResponse, HttpService, Method, Uri}
import org.http4s.dsl.io._

object Service
{
  def payload = "payload"

  def handler(request: Request): IO[Either[String, Response]] =
    IO.pure(Right(Response.ok(payload)))

  def http: Http[IO] = Http.fromConfig(HttpConfig(PureHttp(handler), NoMetrics()))

  def routes: PartialFunction[HRequest[IO], IO[HResponse[IO]]] = {
    case GET -> Root / "act" =>
      for {
        r1 <- http.get("anything", "act")
        r2 <- r1 match {
          case Right(Response(_, data, _, _)) => Ok(data)
          case Left(_) => InternalServerError("boom")
        }
      } yield r2
  }

  def service: HttpService[IO] =
    HttpService[IO](routes)
}

class MetricsSpec
extends Specification
{
  def is = s2"""
  integrate into http4s $integrate
  """

  def integrate = {
    val io = Service.service.orNotFound.run(HRequest(method = Method.GET, uri = Uri.uri("/act")))
    io.flatMap(a => a.as[String]).unsafeRunSync must_== Service.payload
  }
}
