package chm

import cats.ApplicativeError
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.applicativeError._
import cats.effect.Sync
import cats.free.FreeT

object RequestMetrics
{
  def responseMetric[F[_]: Sync, In, Out: HttpResponse]
  (metric: RequestMetric[F, In, Out], response: Out)
  : Metrics.Step[F, Unit] = {
    for {
      _ <- Metrics.mark[F](HttpResponse[Out].status(response).toString)
      _ <- metric.error(response)
    } yield ()
  }

  def resultMetrics[F[_]: Sync, In, Out: HttpResponse]
  (metric: RequestMetric[F, In, Out], result: Either[Throwable, Out])
  : Metrics.Step[F, Unit] =
    result match {
      case Right(r) => responseMetric(metric, r)
      case Left(error) => Metrics.mark[F]("fatal")
    }

  def wrapRequest[F[_]: Sync, M, In, Out: HttpResponse]
  (resources: M)
  (metrics: Metrics[F, M])
  (task: RequestTask[F, In, Out])
  (request: () => F[Out])
  : F[Out] = {
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
