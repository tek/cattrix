package chm

import cats.ApplicativeError
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.applicativeError._
import cats.effect.Sync
import cats.free.FreeT

object RequestMetrics
{
  def responseMetric[F[_]: Sync](metric: RequestMetric[F], response: Response): Metrics.Step[F, Unit] = {
    for {
      _ <- Metrics.mark[F](response.status.toString)
      _ <- metric.error(response)
    } yield ()
  }

  def resultMetrics[F[_]: Sync](metric: RequestMetric[F], result: Either[Throwable, Either[String, Response]])
  : Metrics.Step[F, Unit] =
    result match {
      case Right(Right(r)) => responseMetric(metric, r)
      case Right(Left(r)) => Metrics.mark[F]("fatal")
      case Left(error) => Metrics.mark[F]("fatal")
    }

  def wrapRequest[F[_]: Sync, M]
  (resources: M)
  (metrics: Metrics[F, M])
  (task: RequestTask[F])
  (request: () => F[Either[String, Response]])
  : F[Either[String, Response]] = {
    val name = task.metric.name(task)
    val steps = for {
      t <- Metrics.timer("time")
      _ <- Metrics.incCounter("active")
      response <- Metrics.attempt(request)
      _ <- Metrics.decCounter("active")
      _ <- Metrics.time(t)
      _ <- resultMetrics(task.metric, response)
      result <- Metrics.result(response)
    } yield result
    steps.foldMap(metrics.interpreter(MetricTask(resources, name)))
  }
}
