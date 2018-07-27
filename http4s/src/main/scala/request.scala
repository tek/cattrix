package cattrix

import cats.{ApplicativeError, Applicative}
import cats.data.EitherT
import cats.syntax.functor._
import cats.syntax.flatMap._
import cats.syntax.either._
import cats.syntax.foldable._
import cats.instances.option._
import cats.instances.list._
import cats.effect.{Effect, Sync}
import fs2.{Stream, text}
import org.http4s.{Request => HRequest, Response => HResponse, Method, Uri, ParseResult, EmptyBody, Header => HHeader}
import org.http4s.{Headers, BasicCredentials}
import org.http4s.headers.Authorization
import org.http4s.util.CaseInsensitiveString
import org.http4s.client.blaze.Http1Client

case class Http4sRequest()

object Http4sRequest
extends Http4sRequestInstances
{
  def execute[F[_]: Effect](request: HRequest[F]): EitherT[F, String, HResponse[F]] = {
    for {
      result <- EitherT(makeRequest[F](request))
    } yield result
  }

  /**
   * `Http1Client.stream` takes care of resource freeing after the request, so the call to `fetch` has to be done in the
   * same stream
   */
  def makeRequest[F[_]: Effect](request: HRequest[F]): F[Either[String, HResponse[F]]] = {
    val s = for {
      client <- Http1Client.stream[F]()
      result <- Stream.eval(client.fetch(request)(Applicative[F].pure))
    } yield result
    s.compile.last.map {
      case Some(a) => Right(a)
      case None => Left(s"no stream output in http4s client call for $request")
    }
  }
}

trait Http4sRequestInstances
{
  implicit def HttpIO_Http4sRequest[F[_]: Effect]: HttpIO[F, Http4sRequest, HRequest[F], HResponse[F]] =
    new HttpIO[F, Http4sRequest, HRequest[F], HResponse[F]] {
      def execute(resources: Http4sRequest)(request: HRequest[F]): F[HResponse[F]] =
        for {
          e <- Http4sRequest.execute[F](request).value
          result <- Fatal.fromEither(e)
        } yield result
    }
}

object Http4sInstances
{
  implicit def HttpRequest_HRequest[F[_]: Sync]: HttpRequest[F, HRequest[F]] =
    new HttpRequest[F, HRequest[F]] {
      def cons(method: String, url: String, body: Option[String], auth: Option[Auth], headers: List[Header])
      : Either[String, HRequest[F]] = {
        val b = body.map(a => Stream.emit(a).through(text.utf8Encode)).combineAll
        val a = auth.map(a => List(Authorization(BasicCredentials(a.user, a.password)))).combineAll
        val h = a ++ headers.collect {
          case Header(name, List(value)) => HHeader.Raw(CaseInsensitiveString(name), value)
        }
        val e = for {
          m <- Method.fromString(method.toUpperCase)
          u <- Uri.fromString(url)
        } yield HRequest[F](method = m, uri = u, body = b, headers = Headers(h))
        e.leftMap(_.toString)
      }

      def toRequest(req: HRequest[F]): F[Request] =
        for {
          body <- req.as[String]
        } yield Request(
          req.method.renderString,
          req.uri.renderString,
          Some(body),
          None,
          req.headers.map(a => Header(a.name.value, List(a.value))).toList,
        )
    }

  implicit def HttpResponse_HResponse[F[_]: Sync]: HttpResponse[F, HResponse[F]] =
    new HttpResponse[F, HResponse[F]] {
      def status(response: HResponse[F]): Int = response.status.code

      def cons(res: HResponse[F]): F[Response] =
        for {
          body <- res.as[String]
        } yield Response(res.status.code, body, Nil, Nil)
    }
}
