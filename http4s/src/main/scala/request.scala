package chm

import cats.MonadError
import cats.data.EitherT
import cats.syntax.functor._
import cats.syntax.flatMap._
import cats.syntax.either._
import cats.effect.{Effect, Sync}
import fs2.{Stream, text}
import org.http4s.{Request => HRequest, Response => HResponse, Method, Uri, ParseResult, EmptyBody, Header => HHeader}
import org.http4s.{Headers, BasicCredentials}
import org.http4s.headers.Authorization
import org.http4s.util.CaseInsensitiveString
import org.http4s.client.blaze.Http1Client

case class Http4sRequest[F[_]]()

object Http4sRequest
{
  implicit def HttpIO_Http4sRequest[F[_]: Effect]: HttpIO[F, Http4sRequest, Request, Response] =
    new HttpIO[F, Http4sRequest, Request, Response] {
      def execute(resources: Http4sRequest[F])(request: Request): F[Response] =
        for {
          e <- Http4sRequest.execute[F](request).value
          result <- MonadError[F, Throwable].fromEither(e.leftMap(new Exception(_)))
        } yield result
    }

  def execute[F[_]: Effect](request: Request): EitherT[F, String, Response] = {
    for {
      hrequest <- EitherT.fromEither[F](transformRequest[F](request).leftMap(_.toString))
      result <- EitherT(makeRequest[F](hrequest))
    } yield result
  }

  def transformRequest[F[_]](request: Request): ParseResult[HRequest[F]] = {
    val body = request.body match {
      case Some(data) => Stream.emit(data).through(text.utf8Encode)
      case None => EmptyBody
    }
    val auth = request.auth match {
      case Some(a) => List(Authorization(BasicCredentials(a.user, a.password)))
      case None => Nil
    }
    val headers = auth ++ request.headers.collect {
      case Header(name, List(value)) => HHeader.Raw(CaseInsensitiveString(name), value)
    }
    for {
      method <- Method.fromString(request.method.toUpperCase)
      uri <- Uri.fromString(request.url)
    } yield HRequest[F](method = method, uri = uri, body = body, headers = Headers(headers))
  }

  /**
   * `Http1Client.stream` takes care of resource freeing after the request, so the call to `fetch` has to be done in the
   * same stream
   */
  def makeRequest[F[_]: Effect](request: HRequest[F]): F[Either[String, Response]] = {
    val s = for {
      client <- Http1Client.stream[F]()
      result <- Stream.eval(client.fetch(request)(transformResponse[F]))
    } yield result
    s.compile.last.map {
      case Some(a) => Right(a)
      case None => Left(s"no stream output in http4s client call for $request")
    }
  }

  def transformResponse[F[_]: Sync](response: HResponse[F]): F[Response] =
    for {
      body <- response.as[String]
    } yield Response(response.status.code, body, Nil, Nil)
}
