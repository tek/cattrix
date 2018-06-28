package chm

import cats.{Eval, ApplicativeError}
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.applicativeError._
import cats.effect.Sync
import cats.free.FreeT

object RequestMetrics
{
  def responseMetric[F[_]: Sync](metric: RequestMetric, response: Response): Metrics.Step[F, Unit] = {
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
              Metrics.unit[F]
          }
        case _ =>
          Metrics.unit[F]
      }
    } yield ()
  }

  def resultMetrics[F[_]: Sync](metric: RequestMetric, result: Either[Throwable, Either[String, Response]])
  : Metrics.Step[F, Unit] =
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
    val steps: Metrics.Step[F, Either[String, Response]] = for {
      t <- Metrics.timer[F]("requestTimer")
      _ <- Metrics.incCounter[F]("activeRequests")
      response <- Metrics.run(thunk.map(_.attempt))
      _ <- Metrics.decCounter[F]("activeRequests")
      _ <- Metrics.time[F](t)
      _ <- resultMetrics[F](metric, response)
      result <- FreeT.liftT(ApplicativeError[F, Throwable].fromEither(response))
    } yield result
    steps.foldMap(metrics.interpreter(resources))
  }
}
