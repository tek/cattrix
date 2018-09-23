package cattrix

import scala.concurrent.ExecutionContext

import cats.Applicative
import cats.syntax.functor._
import cats.syntax.either._
import cats.syntax.foldable._
import cats.instances.option._
import cats.instances.list._
import cats.effect.{Sync, ConcurrentEffect}
import fs2.{Stream, text}
import org.http4s.{Request => HRequest, Response => HResponse, Method, Uri, Header => HHeader}
import org.http4s.{Headers, BasicCredentials}
import org.http4s.headers.Authorization
import org.http4s.util.CaseInsensitiveString
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder

case class Http4sRequest[F[_]](client: Client[F])

object Http4sRequest
extends Http4sRequestInstances
{
  def withClient[F[_]: ConcurrentEffect, M, A]
  (metrics: M)
  (thunk: Http[F, HRequest[F], HResponse[F]] => F[A])
  (client: Client[F])
  (implicit mt: Metrics[F, M])
  : F[A] = {
    import Http4sInstances._
    thunk(Http.fromConfig[F](HttpConfig(Http4sRequest(client), metrics)))
  }

  def bracket[F[_]: ConcurrentEffect, M, A](metrics: M)
  (thunk: Http[F, HRequest[F], HResponse[F]] => F[A])
  (implicit mt: Metrics[F, M], ec: ExecutionContext)
  : F[A] =
    BlazeClientBuilder(ec).resource.use(withClient(metrics)(thunk))
}

trait Http4sRequestInstances
{
  implicit def HttpIO_Http4sRequest[F[_]: ConcurrentEffect]: HttpIO[F, Http4sRequest[F], HRequest[F], HResponse[F]] =
    new HttpIO[F, Http4sRequest[F], HRequest[F], HResponse[F]] {
      def execute(resources: Http4sRequest[F])(request: HRequest[F]): F[HResponse[F]] =
        resources.client.fetch(request)(Applicative[F].pure)
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
