package chm

import scala.util.{Success, Failure}
import scala.concurrent.Future

import cats.{Monad, ApplicativeError, Applicative}
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.applicativeError._
import cats.effect.Sync
import cats.free.FreeT
import com.codahale.metrics.{MetricRegistry, Counter, Meter, Timer, SharedMetricRegistries}
import org.log4s.getLogger

case class HttpConfig[F[_], R[_[_]], M](request: R[F], metrics: M)

case class Http[F[_]](
  request: Request => F[Either[String, Response]],
  metrics: RequestTask[F] => (() => F[Either[String, Response]]) => F[Either[String, Response]],
  )
{
  def execute(task: RequestTask[F])(implicit S: Sync[F]): F[Either[String, Response]] =
    Http.execute(this, task)

  def request(request: Request, metric: String)(implicit S: Sync[F]): F[Either[String, Response]] =
    execute(RequestTask.metric[F](request, metric))

  def get(url: String, metric: String)(implicit S: Sync[F]): F[Either[String, Response]] =
    Http.get(this, url, metric)
}

object Http
{
  val log = getLogger("http")

  def fromConfig[F[_]: Sync, R[_[_]], M](conf: HttpConfig[F, R, M])
  (implicit httpRequest: HttpRequest[F, R], metrics: Metrics[F, M])
  : Http[F] =
    Http(httpRequest.execute(conf.request), RequestMetrics.wrapRequest[F, M](conf.metrics)(metrics))

  def execute[F[_]](http: Http[F], task: RequestTask[F])(implicit S: Sync[F]) = {
    log.debug(task.toString)
    http.metrics(task)(() => http.request(task.request))
      .map { response =>
        response match {
          case Right(rs) if (rs.status >= 400) =>
            Http.log.error(s"request failed: $task || response: $rs")
          case _ =>
        }
        response
      }
  }

  def simpleTask[F[_]: Applicative](method: String, url: String, metric: String, body: Option[String]): RequestTask[F] =
    RequestTask.metric[F](Request(method, url, body, None, Nil), metric)

  def get[F[_]](http: Http[F], url: String, metric: String)(implicit S: Sync[F]) =
    execute(http, simpleTask("get", url, metric, None))
}
