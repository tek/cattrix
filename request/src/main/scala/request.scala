package chm

import cats.Applicative
import cats.effect.Sync
import simulacrum.typeclass

case class Auth(user: String, password: String)

case class Header(name: String, values: List[String])

case class Cookie(name: String, value: String)

@typeclass
trait HttpRequest[A]
{
  def cons(method: String, url: String, body: Option[String], auth: Option[Auth], headers: List[Header])
  : Either[String, A]
}

object HttpRequest
{
  def fromRequest[A: HttpRequest](request: Request): Either[String, A] =
    HttpRequest[A].cons(request.method, request.url, request.body, request.auth, request.headers)
}

case class Request(method: String, url: String, body: Option[String], auth: Option[Auth], headers: List[Header])

object Request
{
  def dummy = Request("", "", None, None, Nil)

  def get(url: String): Request = Request("get", url, None, None, Nil)

  implicit def HttpRequest_Request: HttpRequest[Request] =
    new HttpRequest[Request] {
      def cons(method: String, url: String, body: Option[String], auth: Option[Auth], headers: List[Header])
      : Either[String, Request] =
        Right(Request(method, url, body, auth, headers))
    }
}

trait HttpIO[F[_], R, In, Out]
{
  def execute(resources: R)(request: In): F[Out]
}

case class PureHttp[F[_], In, Out](handler: In => F[Out])

object PureHttp
{
  implicit def HttpIO_PureHttp_default[F[_]: Sync, A]
  : HttpIO[F, PureHttp[F, Request, Response], Request, Response] =
    new HttpIO[F, PureHttp[F, Request, Response], Request, Response] {
      def execute(resources: PureHttp[F, Request, Response])(request: Request): F[Response] =
        resources.handler(request)
    }

  def notFound(request: Request): Response =
    Response.notFound(s"PureHttp handler doesn't produce a response for $request")

  def partial[F[_]: Applicative](handler: PartialFunction[Request, F[Response]]): PureHttp[F, Request, Response] =
    PureHttp { request =>
      handler
        .lift(request)
        .getOrElse(Applicative[F].pure(notFound(request)))
    }

  def strict[F[_]: Applicative](data: Map[Request, Response]): PureHttp[F, Request, Response] =
    PureHttp(a => Applicative[F].pure(data.get(a).getOrElse(notFound(a))))
}
