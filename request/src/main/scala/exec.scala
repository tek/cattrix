package chm

import scala.util.{Success, Failure}
import scala.concurrent.Future

import cats.{Monad, Eval, ApplicativeError, Applicative}
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.applicativeError._
import cats.effect.Sync
import com.codahale.metrics.{MetricRegistry, Counter, Meter, Timer, SharedMetricRegistries}
import org.log4s.getLogger

object RequestMetrics
{
  def responseMetric[F[_]: Sync](metric: RequestMetric, response: Response): MetricsEval[F, Unit] = {
    for {
      _ <- Metrics.mark[F](response.status.toString)
      _ <- metric match {
        case StaticRequestMetric(_, Some(name)) if (response.status >= 400) =>
          val statusGroup = if (response.status < 500) "4xx" else "5xx"
          for {
            _ <- Metrics.mark[F](statusGroup)
            _ <- Metrics.incCounter[F](name)
          } yield ()
        case DynamicRequestMetric(_, err) =>
          err(response) match {
            case Some(metric) =>
              Metrics.incCounter[F](metric)
            case None =>
              MetricsEval.unit[F]
          }
        case _ =>
              MetricsEval.unit[F]
      }
    } yield ()
  }

  // def initMetrics(registry: MetricRegistry, task: RequestTask)(implicit ec: ExecutionContext): F[MetricData] = {
  //   val name = task.metric match {
  //     case StaticRequestMetric(name, _) =>
  //       name
  //     case DynamicRequestMetric(nameF, _) =>
  //       nameF(task)
  //   }
  //   for {
  //     timer <- Metrics.requestTimer(name)(registry)
  //     _ <- Metrics.inc(name, "activeRequests")(registry)
  //     timerContext <- F(timer.time())
  //   } yield MetricData(name, timerContext)
  // }

  // def resultMetric(result: Either[Throwable, Either[String, Response]], task: RequestTask, data: MetricData)
  // (registry: MetricRegistry)
  // (implicit ec: ExecutionContext)
  // : F[Unit] = {
  //   result match {
  //     case Right(a) =>
  //       a match {
  //         case Right(response) =>
  //           meterResponse(task, data.name, response)(registry)
  //         case Left(error) =>
  //           Metrics.inc(data.name, "fatal")(registry)
  //       }
  //     case Left(exc) =>
  //       Metrics.inc(data.name, "fatal")(registry)
  //   }
  // }

  def resultMetrics[F[_]: Sync](metric: RequestMetric, result: Either[Throwable, Either[String, Response]])
  : MetricsEval[F, Unit] =
    result match {
      case Right(Right(r)) => responseMetric(metric, r)
      case Right(Left(r)) => Metrics.mark[F]("fatal")
      case Left(error) => Metrics.mark[F]("fatal")
    }

  def wrap[F[_]: Sync, M]
  (resources: M)
  (metric: RequestMetric)
  (thunk: Eval[F[Either[String, Response]]])
  (implicit metrics: Metrics[F, M])
  : F[Either[String, Response]] = {
    val steps: MetricsEval[F, Either[String, Response]] = for {
      t <- Metrics.timer[F]("requestTimer")
      _ <- Metrics.incCounter[F]("activeRequests")
      response <- MetricsEval.run(thunk.map(_.attempt))
      _ <- Metrics.decCounter[F]("activeRequests")
      _ <- Metrics.time[F](t)
      _ <- resultMetrics[F](metric, response)
      result <- MetricsEval.lift(ApplicativeError[F, Throwable].fromEither(response))
    } yield result
    MetricsEval.io(resources)(steps)
  }
}

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
