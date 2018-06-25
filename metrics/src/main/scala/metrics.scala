package chm

import cats.Applicative
import cats.effect.Sync
import simulacrum.typeclass

@typeclass
trait Metrics[A]
{
  def run[F[_]: Sync](metrics: A)(metric: MetricAction): F[Unit]
}

object Metrics
{
  def incCounter[F[_]](name: String): MetricsEval[F, Unit] =
    MetricsEval.Metric(IncCounter(name))

  def decCounter[F[_]](name: String): MetricsEval[F, Unit] =
    MetricsEval.Metric(DecCounter(name))
}

case class NoMetrics()

object NoMetrics
{
  implicit def Metrics_NoMetrics: Metrics[NoMetrics] =
    new Metrics[NoMetrics] {
      def run[F[_]: Sync](metrics: NoMetrics)(metric: MetricAction): F[Unit] =
        Applicative[F].pure(())
    }

}

case class CustomMetrics[F[_], A](handler: (() => F[Either[String, A]]) => F[Either[String, A]])
