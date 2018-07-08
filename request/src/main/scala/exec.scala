package chm

import scala.util.{Success, Failure}
import scala.concurrent.Future

import cats.{Monad, ApplicativeError, Applicative}
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.either._
import cats.effect.Sync
import cats.free.FreeT
import com.codahale.metrics.{MetricRegistry, Counter, Meter, Timer, SharedMetricRegistries}
import org.log4s.getLogger

case class HttpConfig[F[_], R[_[_]], M](request: R[F], metrics: M)

case class Http[F[_], In, Out](
  exec: In => F[Out],
  metrics: RequestTask[F, In, Out] => (() => F[Out]) => F[Out],
  )
{
  def execute(task: RequestTask[F, In, Out])
  (implicit S: Sync[F], req: HttpRequest[In], res: HttpResponse[Out])
  : F[Out] =
    Http.execute(this, task)

  def request(request: Request, metric: String)
  (implicit S: Sync[F], req: HttpRequest[In], res: HttpResponse[Out])
  : F[Out] =
    for {
      native <- ApplicativeError[F, Throwable].fromEither(HttpRequest.fromRequest(request).leftMap(new Exception(_)))
      result <- execute(RequestTask.metric[F, In, Out](native, metric))
    } yield result

  def native(request: In, metric: String)
  (implicit S: Sync[F], req: HttpRequest[In], res: HttpResponse[Out])
  : F[Out] =
    execute(RequestTask.metric[F, In, Out](request, metric))

  def get(url: String, metric: String)
  (implicit S: Sync[F], req: HttpRequest[In], res: HttpResponse[Out])
  : F[Out] =
    Http.get(this, url, metric)
}

object Http
{
  val log = getLogger("http")

  def fromConfig[F[_]: Sync, R[_[_]], M, In, Out: HttpResponse](conf: HttpConfig[F, R, M])
  (implicit httpIO: HttpIO[F, R, In, Out], metrics: Metrics[F, M])
  : Http[F, In, Out] =
    Http(httpIO.execute(conf.request), RequestMetrics.wrapRequest[F, M, In, Out](conf.metrics)(metrics))

  def execute[F[_], In, Out: HttpResponse]
  (http: Http[F, In, Out], task: RequestTask[F, In, Out])
  (implicit S: Sync[F])
  : F[Out] = {
    log.debug(task.toString)
    http.metrics(task)(() => http.exec(task.request))
      .map { response =>
        response match {
          case rs if (HttpResponse[Out].status(rs) >= 400) =>
            Http.log.error(s"request failed: $task || response: $rs")
          case _ =>
        }
        response
      }
  }

  def simpleTask[F[_]: Applicative, In: HttpRequest, Out]
  (method: String, url: String, metric: String, body: Option[String])
  : Either[String, RequestTask[F, In, Out]] =
    for {
      native <- HttpRequest[In].cons(method, url, body, None, Nil)
    } yield RequestTask.metric[F, In, Out](native, metric)

  def get[F[_], In: HttpRequest, Out: HttpResponse]
  (http: Http[F, In, Out], url: String, metric: String)
  (implicit S: Sync[F])
  : F[Out] =
    for {
      task <- ApplicativeError[F, Throwable].fromEither(
        simpleTask[F, In, Out]("get", url, metric, None).leftMap(new Exception(_))
      )
      result <- execute(http, task)
    } yield result
}
