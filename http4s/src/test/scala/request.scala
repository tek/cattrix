package cattrix

import java.net.ServerSocket
import scala.concurrent.ExecutionContext

import cats.data.{Kleisli, OptionT}
import cats.effect.{IO, ContextShift}
import cats.effect.internals.IOContextShift
import org.specs2.Specification
import fs2.text
import org.http4s.{Request => HRequest, Response => HResponse, HttpRoutes, Method, Uri, AuthedService, AuthedRequest}
import org.http4s.headers.Authorization
import org.http4s.BasicCredentials
import org.http4s.dsl.io._
import org.http4s.server.{Server, AuthMiddleware}
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.circe._
import io.circe.syntax._

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

case class CustomerId(id: Int)

case class Customer(id: CustomerId, name: String)

object Data
{
  def payload = "payload"
  def allowedUser = "user1"
  def invalidUserName = "user2"
  def invalidUser(name: String): String = s"invalid user $name"
  def missingHeader = "missing auth header"
  def invalidHeader = "invalid auth header"
  def customer = Customer(CustomerId(5), "Jason")
}

object Remote
{
  import io.circe.generic.auto._

  def authRoutes: PartialFunction[AuthedRequest[IO, Unit], IO[HResponse[IO]]] = {
    case GET -> Root / "resource" as _ => Ok(payload)
  }

  def freeRoutes: PartialFunction[HRequest[IO], IO[HResponse[IO]]] = {
    case GET -> Root / "json" => Ok(customer.asJson)
  }

  val auth: Kleisli[IO, HRequest[IO], Either[String, Unit]] =
    Kleisli { request =>
      val result = for {
        authHeader <- request.headers.get(Authorization).toRight(missingHeader)
        _ <- authHeader match {
          case Authorization(BasicCredentials(username, _)) =>
            if (username == allowedUser) Right(())
            else Left(invalidUser(username))
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

  def authService: HttpRoutes[IO] = middleware(AuthedService[Unit, IO](authRoutes))

  def freeService: HttpRoutes[IO] = HttpRoutes.of[IO](freeRoutes)

  def serve(port: Int)(implicit cs: ContextShift[IO]): IO[Server[IO]] = {
    BlazeBuilder[IO]
      .bindHttp(port, "localhost")
      .mountService(authService, "/auth")
      .mountService(freeService, "/free")
      .start
  }
}

object Local
{
  import io.circe.generic.auto._

  def remote(port: Int) = s"http://localhost:$port"

  def test(http: Http[IO, HRequest[IO], HResponse[IO]], port: Int)(creds: Option[Auth])
  (implicit cs: ContextShift[IO])
  : IO[HResponse[IO]] = {
    val request = Request("get", s"${remote(port)}/auth/resource", None, creds, Nil)
    for {
      nativeRequest <- Fatal.fromEither[IO, HRequest[IO]](HttpRequest.fromRequest[IO, HRequest[IO]](request))
      response <- http.native.request(nativeRequest, "resource")
    } yield response
  }

  def routes(http: Http[IO, HRequest[IO], HResponse[IO]], port: Int)
  (implicit cs: ContextShift[IO])
  : PartialFunction[HRequest[IO], IO[HResponse[IO]]] = {
    case GET -> Root / "invalid" => test(http, port)(Some(Auth(invalidUserName, "")))
    case GET -> Root / "valid" => test(http, port)(Some(Auth(allowedUser, "")))
    case GET -> Root / "none" => test(http, port)(None)
    case GET -> Root / "json" =>
      for {
        customer <- http.getAs[Customer](s"${remote(port)}/free/json", "json")
        response <- customer match {
          case JsonResponse.Successful(a) => Ok(a.asJson)
          case a => InternalServerError(a.toString)
        }
      } yield response
  }

  def service(http: Http[IO, HRequest[IO], HResponse[IO]], port: Int)
  (implicit cs: ContextShift[IO])
  : HttpRoutes[IO] =
    HttpRoutes.of[IO](routes(http, port))
}

class RequestSpec(implicit ec: ExecutionContext)
extends Specification
{
  def is = s2"""
  authed request with valid creds $valid
  authed request with invalid creds $invalid
  authed request without creds $noCreds
  json request $json
  """

  implicit val contextShift: ContextShift[IO] =
    IOContextShift(ec)

  def execute(port: Int, path: String)(http: Http[IO, HRequest[IO], HResponse[IO]]): IO[(Int, String)] =
    for {
      server <- Remote.serve(port)
      uri <- IO.fromEither(Uri.fromString(s"/$path"))
      response <- Local.service(http, port).orNotFound.run(HRequest(method = Method.GET, uri = uri))
      _ <- server.shutdown
      body <- response.as[String]
    } yield (response.status.code, body)

  def test(path: String): IO[(Int, String)] =
    Http4sRequest.bracket(NoMetrics())(execute(Util.freePort(), path))

  def valid = test("valid").unsafeRunSync must_== (200 -> payload)

  def invalid = test("invalid").unsafeRunSync must_== (403 -> invalidUser(invalidUserName))

  def noCreds = test("none").unsafeRunSync must_== (403 -> missingHeader)

  def json = test("json").unsafeRunSync must_== (200 -> s"""{"id":{"id":5},"name":"Jason"}""")
}
