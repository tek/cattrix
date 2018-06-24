package sf

import cats.{Monad, Eval, Applicative}
import cats.effect.Sync
import cats.syntax.all._

sealed trait MetricsEval[F[_], A]

object MetricsEval
extends MetricsEvalInstances
{
  case class Metric[F[_]](metric: MetricAction)
  extends MetricsEval[F, Unit]

  case class Suspend[F[_], A](thunk: Eval[F[A]])
  extends MetricsEval[F, A]

  case class Bind[F[_], A, B](head: MetricsEval[F, B], tail: B => MetricsEval[F, A])
  extends MetricsEval[F, A]
  {
    def run[M: Metrics](metrics: M)(implicit M: Monad[F], S: Sync[F]): F[A] =
      for {
        a <- MetricsEval.io(metrics)(head)
        b <- MetricsEval.io(metrics)(tail(a))
      } yield b
  }

  def run[F[_], A](thunk: Eval[F[A]]): MetricsEval[F, A] = Suspend(thunk)

  def io[F[_]: Sync: Monad, A, M: Metrics](metrics: M)(fa: MetricsEval[F, A]): F[A] = {
    val run = Metrics[M].run[F](metrics) _
    fa match {
      case Metric(metric) => run(metric)
      case Suspend(thunk) => thunk.value
      case s @ Bind(head, tail) => s.run(metrics)
    }
  }
}

trait MetricsEvalInstances
{
  implicit def Monad_MetricsEval[F[_]: Monad]: Monad[MetricsEval[F, ?]] =
    new Monad[MetricsEval[F, ?]] {
      def pure[A](a: A): MetricsEval[F, A] = MetricsEval.Suspend(Eval.later(Applicative[F].pure(a)))

      def flatMap[A, B](fa: MetricsEval[F, A])(f: A => MetricsEval[F, B]): MetricsEval[F, B] =
        MetricsEval.Bind(fa, f)

      def tailRecM[A, B](a: A)(f: A => MetricsEval[F, Either[A, B]]): MetricsEval[F, B] = ???
    }
}
