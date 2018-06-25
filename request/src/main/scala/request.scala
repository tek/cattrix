package chm

import cats.Applicative
import cats.effect.Sync

case class Auth(user: String, password: String)

case class Header(name: String, values: List[String])

case class Cookie(name: String, value: String)

case class Request(method: String, url: String, body: Option[String], auth: Option[Auth], headers: List[Header])

object Request
{
  def dummy = Request("", "", None, None, Nil)

  def get(url: String): Request = Request("get", url, None, None, Nil)
}

case class Response(status: Int, body: String, headers: List[Header], cookies: List[Cookie])

object Response
{
  def ok(body: String): Response = Response(200, body, Nil, Nil)

  def notFound(body: String): Response = Response(404, body, Nil, Nil)

  def err(body: String): Response = Response(503, body, Nil, Nil)

  def header(name: String)(response: Response): Option[List[String]] =
    response.headers.find(_.name == name).map(_.values)

  def header1(name: String)(response: Response): Option[String] =
    for {
      values <- header(name)(response)
      value <- values.headOption
    } yield value
}

trait HttpRequest[F[_], R[_[_]]]
{
  def execute(resources: R[F])(request: Request): F[Either[String, Response]]
}

case class PureHttp[F[_]](handler: Request => F[Either[String, Response]])

object PureHttp
{
  implicit def HttpRequest_PureHttp[F[_]: Sync, A]: HttpRequest[F, PureHttp] =
    new HttpRequest[F, PureHttp] {
      def execute(resources: PureHttp[F])(request: Request): F[Either[String, Response]] =
        resources.handler(request)
    }

  def notFound(request: Request): Response =
    Response.notFound(s"PureHttp handler doesn't produce a response for $request")

  def partial[F[_]: Applicative](handler: PartialFunction[Request, F[Either[String, Response]]]): PureHttp[F] =
    PureHttp { request =>
      handler
        .lift(request)
        .getOrElse(Applicative[F].pure(Right(notFound(request))))
    }

  def strict[F[_]: Applicative](data: Map[Request, Response]): PureHttp[F] =
    PureHttp(a => Applicative[F].pure(Right(data.get(a).getOrElse(notFound(a)))))
}
