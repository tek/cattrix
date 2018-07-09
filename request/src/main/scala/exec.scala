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

case class HttpConfig[R, M](request: R, metrics: M)

case class NativeHttp[F[_], In, Out](
  exec: In => F[Out],
  metrics: RequestTask[F, In, Out] => (() => F[Out]) => F[Out],
)
{
  def task(task: RequestTask[F, In, Out])(implicit S: Sync[F], res: HttpResponse[F, Out]): F[Out] =
    NativeHttp.execute(this, task)

  def request(request: In, metric: String)(implicit S: Sync[F], res: HttpResponse[F, Out]): F[Out] =
    task(RequestTask.metric[F, In, Out](request, metric))

  def get(url: String, metric: String)(implicit S: Sync[F], req: HttpRequest[F, In], res: HttpResponse[F, Out])
  : F[Out] =
    NativeHttp.get(this, url, metric)
}

object NativeHttp
{
  val log = getLogger("http")

  def execute[F[_]: Sync, In, Out]
  (http: NativeHttp[F, In, Out], task: RequestTask[F, In, Out])
  (implicit res: HttpResponse[F, Out])
  : F[Out] = {
    log.debug(task.toString)
    http.metrics(task)(() => http.exec(task.request))
      .map { response =>
        response match {
          case rs if (res.status(rs) >= 400) =>
            Http.log.error(s"request failed: $task || response: $rs")
          case _ =>
        }
        response
      }
  }

  def simpleTask[F[_]: Applicative, In, Out]
  (method: String, url: String, metric: String, body: Option[String])
  (implicit req: HttpRequest[F, In])
  : Either[String, RequestTask[F, In, Out]] =
    for {
      native <- req.cons(method, url, body, None, Nil)
    } yield RequestTask.metric[F, In, Out](native, metric)

  def get[F[_], In, Out]
  (http: NativeHttp[F, In, Out], url: String, metric: String)
  (implicit S: Sync[F], req: HttpRequest[F, In], res: HttpResponse[F, Out])
  : F[Out] =
    for {
      task <- Fatal.fromEither(simpleTask[F, In, Out]("get", url, metric, None))
      result <- execute(http, task)
    } yield result
}

case class Http[F[_], In, Out](native: NativeHttp[F, In, Out])
{
  def task(task: RequestTask[F, Request, Out])
  (implicit S: Sync[F], req: HttpRequest[F, In], res: HttpResponse[F, Out])
  : F[Response] =
    Http.native(this, task)

  def request(request: Request, metric: String)
  (implicit S: Sync[F], req: HttpRequest[F, In], res: HttpResponse[F, Out])
  : F[Response] =
    task(RequestTask.metric[F, Request, Out](request, metric))

  def get(url: String, metric: String)
  (implicit S: Sync[F], req: HttpRequest[F, In], res: HttpResponse[F, Out])
  : F[Response] =
    Http.simple(this, url, metric, "get")
}

object Http
{
  val log = getLogger("http")

  class HttpCons[F[_]]
  {
    def apply[R, M, In, Out]
    (conf: HttpConfig[R, M])
    (implicit sync: Sync[F], httpIO: HttpIO[F, R, In, Out], metrics: Metrics[F, M], res: HttpResponse[F, Out])
    : Http[F, In, Out] =
      Http(NativeHttp(httpIO.execute(conf.request), RequestMetrics.wrapRequest[F, M, In, Out](conf.metrics)(metrics)))
  }

  def fromConfig[F[_]]: HttpCons[F] = new HttpCons[F]

  def native[F[_]: Sync, In, Out]
  (http: Http[F, In, Out], task: RequestTask[F, Request, Out])
  (implicit req: HttpRequest[F, In], res: HttpResponse[F, Out])
  : F[Response] =
    for {
      nativeRequest <- Fatal.fromEither(HttpRequest.fromRequest(task.request))
      nativeTask = RequestTask(nativeRequest, RequestMetric.contramapIn(task.metric)(req.toRequest))
      nativeResponse <- http.native.task(nativeTask)
      response <- res.cons(nativeResponse)
    } yield response

  def simple[F[_]: Sync, In, Out]
  (http: Http[F, In, Out], url: String, metric: String, method: String)
  (implicit req: HttpRequest[F, In], res: HttpResponse[F, Out])
  : F[Response] =
    for {
      task <- Fatal.fromEither(NativeHttp.simpleTask[F, Request, Out](metric, url, metric, None))
      result <- native(http, task)
    } yield result
}
