package chm

import scala.util.{Success, Failure}
import scala.concurrent.Future

import cats.{Monad, Eval, ApplicativeError, Applicative}
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
  metrics: RequestMetric => Eval[F[Either[String, Response]]] => F[Either[String, Response]],
  )
{
  def execute(task: RequestTask)(implicit S: Sync[F]): F[Either[String, Response]] =
    Http.execute(this, task)

  def get(url: String, metric: String)(implicit S: Sync[F]): F[Either[String, Response]] =
    Http.get(this, url, metric)
}

object Http
{
  val log = getLogger("sf.http")

  def fromConfig[F[_]: Sync, R[_[_]], M](conf: HttpConfig[F, R, M])
  (implicit httpRequest: HttpRequest[F, R], metrics: Metrics[F, M])
  : Http[F] =
    Http(httpRequest.execute(conf.request), RequestMetrics.wrap[F, M](conf.metrics))

  def execute[F[_]](http: Http[F], task: RequestTask)(implicit S: Sync[F]) = {
    Http.log.debug(task.toString)
    http.metrics(task.metric)(Eval.later(http.request(task.request)))
      .map { response =>
        response match {
          case Right(rs) if (rs.status >= 400) =>
            Http.log.error(s"request failed: $task || response: $rs")
          case _ =>
        }
        response
      }
  }

  def simpleTask(method: String, url: String, metric: String, body: Option[String]): RequestTask =
    RequestTask.metric(Request(method, url, body, None, Nil), metric)

  def get[F[_]](http: Http[F], url: String, metric: String)(implicit S: Sync[F]) =
    execute(http, simpleTask("get", url, metric, None))
}
