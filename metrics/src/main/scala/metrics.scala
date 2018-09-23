package cattrix

import cats.{~>, Applicative, MonadError, ApplicativeError, Monad}
import cats.free.FreeT
import cats.effect.Sync

case class MetricTask[A](resources: A, metric: String)

trait Metrics[F[_], A]
{
  def interpreter(task: MetricTask[A]): Metric[F, ?] ~> F
}

object Metrics
{
  type Step[F[_], A] = FreeT[Metric[F, ?], F, A]

  def incCounter[F[_]: Applicative](name: String): Step[F, Unit] =
    FreeT.liftF(IncCounter(name))

  def decCounter[F[_]: Applicative](name: String): Step[F, Unit] =
    FreeT.liftF(DecCounter(name))

  def timer[F[_]: Applicative](name: String): Step[F, TimerData] =
    FreeT.liftF(StartTimer(name))

  def time[F[_]: Applicative](data: TimerData): Step[F, Unit] =
    FreeT.liftF(StopTimer(data))

  def mark[F[_]: Applicative](name: String): Step[F, Unit] =
    FreeT.liftF(Mark(name))

  def run[F[_]: Applicative, A](thunk: () => F[A]): Step[F, A] =
    FreeT.liftF(Run(thunk))

  def attempt[F[_], A, E](thunk: () => F[A])(implicit ME: MonadError[F, E]): Step[F, Either[E, A]] =
    run(() => ME.attempt(thunk()))

  def unit[F[_]: Applicative]: Step[F, Unit] =
    FreeT.pure(())

  def result[F[_], A](ea: Either[Throwable, A])(implicit AE: ApplicativeError[F, Throwable]): Step[F, A] =
    FreeT.liftT(AE.fromEither(ea))

  def timed[F[_]: Sync, M, A] =
    MetricsPrograms.simpleTimed[F, M, A] _

  def compile[F[_]: Monad, M, A](task: MetricTask[M])(prog: Step[F, A])(implicit metrics: Metrics[F, M]): F[A] =
    prog.foldMap(metrics.interpreter(task))
}

case class NoMetrics()

object NoMetrics
{
  implicit def Metrics_NoMetrics[F[_]: Applicative]: Metrics[F, NoMetrics] =
    new Metrics[F, NoMetrics] {
      def interpreter(task: MetricTask[NoMetrics]): Metric[F, ?] ~> F =
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
          case IncCounter(_) => unit
          case DecCounter(_) => unit
          case StopTimer(_) => unit
          case Mark(_) => unit
          case Run(thunk) => thunk()
        }
      }
    }
}

case class CustomMetrics[F[_], A](handler: (() => F[Either[String, A]]) => F[Either[String, A]])
