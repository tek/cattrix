package cattrix

import cats.effect.IO
import org.specs2.Specification
import fs2.text
import org.http4s.{Request => HRequest, Response => HResponse, HttpRoutes, Method, Uri}
import org.http4s.dsl.io._

object Service
{
  def payload = "payload"

  def handler: Request => IO[Response] = {
    case _ => IO.pure(Response.ok(payload))
  }

  def http: Http[IO, Request, Response] =
    Http.fromConfig(HttpConfig(PureHttp(handler), NoMetrics()))

  def routes: PartialFunction[HRequest[IO], IO[HResponse[IO]]] = {
    case GET -> Root / "act" =>
      for {
        r1 <- http.get("anything", "act")
        r2 <- Ok(r1.body)
      } yield r2
  }

  def service: HttpRoutes[IO] =
    HttpRoutes.of[IO](routes)
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
