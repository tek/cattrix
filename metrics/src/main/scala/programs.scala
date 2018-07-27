package cattrix

import cats.ApplicativeError
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.applicativeError._
import cats.effect.Sync
import cats.free.FreeT

object MetricsPrograms
{
  def errorMetric[F[_]: Sync, A](metric: String, result: Either[Throwable, A])
  : Metrics.Step[F, Unit] =
    result match {
      case Right(Right(r)) => Metrics.unit
      case _ => Metrics.mark[F](metric)
    }

  def simpleTimed[F[_]: Sync, M, A]
  (resources: M)
  (metrics: Metrics[F, M])
  (metric: String, error: String)
  (thunk: => F[A])
  : F[A] = {
    val steps = for {
      t <- Metrics.timer("time")
      _ <- Metrics.mark("count")
      _ <- Metrics.incCounter("active")
      output <- Metrics.attempt(() => thunk)
      _ <- Metrics.decCounter("active")
      _ <- Metrics.time(t)
      _ <- errorMetric(error, output)
      result <- Metrics.result(output)
    } yield result
    steps.foldMap(metrics.interpreter(MetricTask(resources, metric)))
  }
}
