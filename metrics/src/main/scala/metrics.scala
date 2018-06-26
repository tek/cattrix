package chm

import cats.Applicative
import cats.~>
import cats.arrow.FunctionK
import cats.effect.Sync

trait Metrics[F[_], A]
{
  def run[B](metrics: A)(action: MetricAction[F, B]): F[B]
  def interpreter(metrics: A): MetricAction[F, ?] ~> F
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
      def run[A](metrics: NoMetrics)(action: MetricAction[F, A]): F[A] =
        interpreter(metrics)(action)

      def interpreter(metrics: NoMetrics): MetricAction[F, ?] ~> F =
        new (MetricAction[F, ?] ~> F) {
          def apply[A](action: MetricAction[F, A]): F[A] = {
            type FF[A] = F[A]
            val unit = Applicative[FF].pure(())
            action match {
              case StartTimer(name) =>
                Applicative[FF].pure(TimerData(name, 0))
              case IncCounter(name) => unit
              case DecCounter(name) => unit
              case StopTimer(data) => unit
              case Mark(name) => unit
              case Run(thunk) => thunk.value
            }
          }
        }
    }

  def interpreter[F[_]: Applicative]: MetricAction[F, ?] ~> F =
    new (MetricAction[F, ?] ~> F) {
      def apply[A](action: MetricAction[F, A]): F[A] = {
        type FF[A] = F[A]
        val unit = Applicative[FF].pure(())
        action match {
          case StartTimer(name) =>
            Applicative[FF].pure(TimerData(name, 0))
          case IncCounter(name) => unit
          case DecCounter(name) => unit
          case StopTimer(data) => unit
          case Mark(name) => unit
          case Run(thunk) => thunk.value
        }
      }
    }
}

case class CustomMetrics[F[_], A](handler: (() => F[Either[String, A]]) => F[Either[String, A]])
