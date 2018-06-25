package chm

import cats.Applicative
import cats.effect.Sync

trait Metrics[F[_], A]
{
  def run[B](metrics: A)(metric: MetricAction[B]): F[B]
}

object Metrics
{
  def incCounter[F[_]](name: String): MetricsEval[F, Unit] =
    MetricsEval.Metric(IncCounter(name))

  def decCounter[F[_]](name: String): MetricsEval[F, Unit] =
    MetricsEval.Metric(DecCounter(name))

  def timer[F[_]](name: String): MetricsEval[F, TimerData] =
    MetricsEval.Metric(StartTimer(name))

  def time[F[_]](data: TimerData): MetricsEval[F, Unit] =
    MetricsEval.Metric(StopTimer(data))

  def mark[F[_]](name: String): MetricsEval[F, Unit] =
    MetricsEval.Metric(Mark(name))
}

case class NoMetrics()

object NoMetrics
{
  implicit def Metrics_NoMetrics[F[_]: Applicative]: Metrics[F, NoMetrics] =
    new Metrics[F, NoMetrics] {
      def run[A](metrics: NoMetrics)(metric: MetricAction[A]): F[A] =
        interpreter(metric)
    }

    def interpreter[F[_]: Applicative, A](metric: MetricAction[A]): F[A] =
      Applicative[F].pure(
        metric match {
          case StartTimer(name) => TimerData(name, 0)
          case IncCounter(name) =>
          case DecCounter(name) =>
          case StopTimer(data) =>
          case Mark(name) =>
        }
      )
}

case class CustomMetrics[F[_], A](handler: (() => F[Either[String, A]]) => F[Either[String, A]])
