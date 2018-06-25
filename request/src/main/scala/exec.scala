package chm

import scala.util.{Success, Failure}
import scala.concurrent.Future

import cats.{Monad, Eval}
import cats.syntax.all._
import cats.effect.Sync
import com.codahale.metrics.{MetricRegistry, Counter, Meter, Timer, SharedMetricRegistries}
import org.log4s.getLogger

object RequestMetrics
{
  // def meterResponse(task: RequestTask, name: String, response: Response)
  // (registry: MetricRegistry)
  // (implicit ec: ExecutionContext)
  // : F[Unit] = {
  //   for {
  //     _ <- Metrics.statusCode(name)(response.status)(registry)
  //     _ <- task.metric match {
  //       case StaticRequestMetric(_, Some(metric)) if (response.status >= 400) =>
  //         val statusGroup = if (response.status < 500) "4xx" else "5xx"
  //         for {
  //           _ <- Metrics.mark(name, statusGroup)(registry)
  //           _ <- Metrics.inc(name, metric)(registry)
  //         } yield ()
  //       case DynamicRequestMetric(_, err) =>
  //         err(task, Some(response)) match {
  //           case Some(metric) =>
  //             Metrics.inc(name, metric)(registry)
  //           case None =>
  //             F.successful(())
  //         }
  //       case _ =>
  //         F.successful(())
  //     }
  //   } yield ()
  // }

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

  // def finishMetrics(
  //   registry: MetricRegistry,
  //   task: RequestTask,
  //   result: Either[Throwable, Either[String, Response]],
  //   data: MetricData,
  // )
  // (implicit ec: ExecutionContext)
  // : F[Unit] = {
  //   for {
  //     _ <- F(data.timer.stop())
  //     _ <- Metrics.dec(data.name, "activeRequests")(registry)
  //     _ <- resultMetric(result, task, data)(registry)
  //   } yield ()
  // }

  // def chMetered(name: String, task: RequestTask)
  // (thunk: => F[Either[String, Response]])
  // (implicit ec: ExecutionContext)
  // : F[Either[String, Response]] =
  //   for {
  //     registry <- F(SharedMetricRegistries.getOrCreate(name))
  //     data <- initMetrics(registry, task)
  //     result <- thunk.transform {
  //       case Success(value) => Success(Right(value))
  //       case Failure(error) => Success(Left(error))
  //     }
  //     _ <- finishMetrics(registry, task, result, data)
  //     output <- result match {
  //       case Right(value) => F.successful(value)
  //       case Left(error) => F.failed(error)
  //     }
  //   } yield output

  // def metered(metrics: Metrics[Response], task: RequestTask)
  // (thunk: => F[Either[String, Response]])
  // (implicit ec: ExecutionContext)
  // : F[Either[String, Response]] =
  //   metrics match {
  //     case NoMetrics() => thunk
  //     // case CustomMetrics(handler) => handler(() => thunk)
  //     case Codahale(registry) => chMetered(registry, task)(thunk)
  //   }

  def wrap[F[_]: Sync, M: Metrics]
  (metrics: M)
  (thunk: Eval[F[Either[String, Response]]])
  : F[Either[String, Response]] = {
    val steps = for {
      _ <- Metrics.incCounter[F]("activeRequests")
      r <- MetricsEval.run(thunk)
      _ <- Metrics.decCounter[F]("activeRequests")
    } yield r
    MetricsEval.io(metrics)(steps)
  }
}

case class HttpConfig[F[_], R[_[_]], M](request: R[F], metrics: M)

case class Http[F[_]](
  request: Request => F[Either[String, Response]],
  metrics: Eval[F[Either[String, Response]]] => F[Either[String, Response]],
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

  def fromConfig[F[_]: Sync, R[_[_]], M: Metrics](conf: HttpConfig[F, R, M])
  (implicit httpRequest: HttpRequest[F, R])
  : Http[F] =
    Http(httpRequest.execute(conf.request), RequestMetrics.wrap[F, M](conf.metrics))

  def execute[F[_]](http: Http[F], task: RequestTask)(implicit S: Sync[F]) = {
    Http.log.debug(task.toString)
    http.metrics(Eval.later(http.request(task.request)))
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
