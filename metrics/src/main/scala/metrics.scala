package chm

import cats.{~>, Eval, Applicative}
import cats.free.FreeT

trait Metrics[F[_], A]
{
  def run[B](metrics: A)(action: Metric[F, B]): F[B]
  def interpreter(metrics: A): Metric[F, ?] ~> F
}

object Metrics
{
  type Step[F[_], A] = FreeT[Metric[F, ?], F, A]

  def incCounter[F[_]: Applicative](name: String): Step[F, Unit] =
    FreeT.liftF[Metric[F, ?], F, Unit](IncCounter(name))

  def decCounter[F[_]: Applicative](name: String): Step[F, Unit] =
    FreeT.liftF[Metric[F, ?], F, Unit](DecCounter(name))

  def timer[F[_]: Applicative](name: String): Step[F, TimerData] =
    FreeT.liftF[Metric[F, ?], F, TimerData](StartTimer(name))

  def time[F[_]: Applicative](data: TimerData): Step[F, Unit] =
    FreeT.liftF[Metric[F, ?], F, Unit](StopTimer(data))

  def mark[F[_]: Applicative](name: String): Step[F, Unit] =
    FreeT.liftF[Metric[F, ?], F, Unit](Mark(name))

  def run[F[_]: Applicative, A](thunk: Eval[F[A]]): Step[F, A] =
    FreeT.liftF[Metric[F, ?], F, A](Run(thunk))

  def unit[F[_]: Applicative]: Step[F, Unit] =
    FreeT.pure(())
}

case class NoMetrics()

object NoMetrics
{
  implicit def Metrics_NoMetrics[F[_]: Applicative]: Metrics[F, NoMetrics] =
    new Metrics[F, NoMetrics] {
      def run[A](metrics: NoMetrics)(action: Metric[F, A]): F[A] =
        interpreter(metrics)(action)

      def interpreter(metrics: NoMetrics): Metric[F, ?] ~> F =
        NoMetrics.interpreter[F]
    }

  def interpreter[F[_]: Applicative]: Metric[F, ?] ~> F =
    new (Metric[F, ?] ~> F) {
      def apply[A](action: Metric[F, A]): F[A] = {
        val unit = Applicative[F].pure(())
        type M[A] = F[A]
        action match {
          case StartTimer(name) =>
            Applicative[M].pure(TimerData(name, 0))
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
