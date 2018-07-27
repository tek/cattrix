package cattrix

import cats.Applicative
import cats.effect.Sync

case class Auth(user: String, password: String)

case class Header(name: String, values: List[String])

case class Cookie(name: String, value: String)

trait HttpRequest[F[_], A]
{
  def cons(method: String, url: String, body: Option[String], auth: Option[Auth], headers: List[Header])
  : Either[String, A]

  def toRequest(a: A): F[Request]
}

object HttpRequest
{
  def fromRequest[F[_], A](request: Request)(implicit req: HttpRequest[F, A]): Either[String, A] =
    req.cons(request.method, request.url, request.body, request.auth, request.headers)
}

case class Request(method: String, url: String, body: Option[String], auth: Option[Auth], headers: List[Header])

object Request
{
  def dummy = Request("", "", None, None, Nil)

  def get(url: String): Request = Request("get", url, None, None, Nil)

  implicit def HttpRequest_Request[F[_]: Applicative]: HttpRequest[F, Request] =
    new HttpRequest[F, Request] {
      def cons(method: String, url: String, body: Option[String], auth: Option[Auth], headers: List[Header])
      : Either[String, Request] =
        Right(Request(method, url, body, auth, headers))

        def toRequest(a: Request): F[Request] = Applicative[F].pure(a)
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
