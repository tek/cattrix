package chm

import java.net.ServerSocket
import scala.concurrent.ExecutionContext

import cats.Id
import cats.data.{Kleisli, OptionT}
import cats.syntax.all._
import cats.effect.IO
import org.specs2.Specification
import fs2.{Stream, text, StreamApp}
import org.http4s.{Request => HRequest, Response => HResponse, HttpService, Method, Uri, AuthedService, AuthedRequest}
import org.http4s.Status
import org.http4s.headers.Authorization
import org.http4s.BasicCredentials
import org.http4s.dsl.io._
import org.http4s.server.{Server, AuthMiddleware}
import org.http4s.server.blaze.BlazeBuilder

import Data._
import Http4sInstances._

object Util
{
  def freePort() = {
    val socket = new ServerSocket(0)
    socket.setReuseAddress(true)
    val port = socket.getLocalPort()
    socket.close()
    port
  }
}

object Data
{
  val payload = "payload"
  val allowedUser = "user1"
  def invalidUserName = "user2"
  def invalidUser(name: String): String = s"invalid user $name"
  def missingHeader = "missing auth header"
  def invalidHeader = "invalid auth header"
}

object Remote
{
  def routes: PartialFunction[AuthedRequest[IO, Unit], IO[HResponse[IO]]] = {
    case GET -> Root / "resource" as _ => Ok(payload)
  }

  val auth: Kleisli[IO, HRequest[IO], Either[String, Unit]] =
    Kleisli { request =>
      val result = for {
        authHeader <- request.headers.get(Authorization).toRight(missingHeader)
        _ <- authHeader match {
          case Authorization(BasicCredentials(creds)) =>
            if (creds.username == allowedUser) Right(())
            else Left(invalidUser(creds.username))
          case _ => Left(invalidHeader)
        }
      } yield ()
      IO.pure(result)
    }

  def authFailure: AuthedService[String, IO] =
    Kleisli { request =>
      OptionT.liftF(Forbidden(request.authInfo))
    }

  def middleware: AuthMiddleware[IO, Unit] = AuthMiddleware(auth, authFailure)

  def service: HttpService[IO] = middleware(AuthedService[Unit, IO](routes))

  def serve(port: Int)(implicit ec: ExecutionContext): IO[Server[IO]] = {
    BlazeBuilder[IO]
      .bindHttp(port, "localhost")
      .mountService(service, "/")
      .start
  }
}

object Local
{
  def http: Http[IO, HRequest[IO], HResponse[IO]] =
    Http.fromConfig(HttpConfig(Http4sRequest[IO](), NoMetrics()))

  def test(port: Int)(creds: Option[Auth]): IO[HResponse[IO]] =
    http.request(Request("get", s"http://localhost:$port/resource", None, creds, Nil), "resource")

  def routes(port: Int): PartialFunction[HRequest[IO], IO[HResponse[IO]]] = {
    case GET -> Root / "invalid" => test(port)(Some(Auth(invalidUserName, "")))
    case GET -> Root / "valid" => test(port)(Some(Auth(allowedUser, "")))
    case GET -> Root / "none" => test(port)(None)
  }

  def service(port: Int): HttpService[IO] =
    HttpService[IO](routes(port))
}

class RequestSpec(implicit ec: ExecutionContext)
extends Specification
{
  def is = s2"""
  authed request with valid creds $valid
  authed request with invalid creds $invalid
  authed request without creds $noCreds
  """

  def test(path: String): IO[(Int, String)] = {
    val port = Util.freePort()
    for {
      server <- Remote.serve(port)
      uri <- IO.fromEither(Uri.fromString(s"/$path"))
      response <- Local.service(port).orNotFound.run(HRequest(method = Method.GET, uri = uri))
      _ <- server.shutdown
      body <- response.as[String]
    } yield (response.status.code, body)
  }

  def valid = test("valid").unsafeRunSync must_== (200 -> payload)

  def invalid = test("invalid").unsafeRunSync must_== (403 -> invalidUser(invalidUserName))

  def noCreds = test("none").unsafeRunSync must_== (403 -> missingHeader)
}
