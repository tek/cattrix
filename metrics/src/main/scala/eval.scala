package chm

import cats.{Monad, Eval, Applicative, Functor}
import cats.effect.Sync
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.free.FreeT

sealed trait MetricsEval[F[_], A]

object MetricsEval
extends MetricsEvalInstances
{
  case class Pure[F[_], A](value: A)
  extends MetricsEval[F, A]

  case class Metric[F[_], A](metric: MetricAction[F, A])
  extends MetricsEval[F, A]

  case class Suspend[F[_], A](thunk: Eval[F[A]])
  extends MetricsEval[F, A]

  case class Bind[F[_], A, B](head: MetricsEval[F, B], tail: B => MetricsEval[F, A])
  extends MetricsEval[F, A]
  {
    def run[M](resources: M)(implicit metrics: Metrics[F, M], M: Monad[F], S: Sync[F]): F[A] =
      for {
        a <- MetricsEval.io(resources)(head)
        b <- MetricsEval.io(resources)(tail(a))
      } yield b
  }

  def run[F[_], A](thunk: Eval[F[A]]): MetricsEval[F, A] = Suspend(thunk)

  def lift[F[_], A](thunk: => F[A]): MetricsEval[F, A] = run(Eval.later(thunk))

  def unit[F[_]]: MetricsEval[F, Unit] = Pure(())

  def io[F[_]: Sync: Monad, A, M]
  (resources: M)
  (fa: MetricsEval[F, A])
  (implicit metrics: Metrics[F, M])
  : F[A] = {
    fa match {
      case Pure(value) => Applicative[F].pure(value)
      case Metric(metric) => metrics.run(resources)(metric)
      case Suspend(thunk) => thunk.value
      case s @ Bind(head, tail) => s.run(resources)
    }
  }
}

object MetricsEval1
{
  type ME[F[_], A] = FreeT[MetricAction[F, ?], F, A]

  def incCounter[F[_]: Applicative](name: String): ME[F, Unit] =
    FreeT.liftF[MetricAction[F, ?], F, Unit](IncCounter(name))

  def decCounter[F[_]: Applicative](name: String): ME[F, Unit] =
    FreeT.liftF[MetricAction[F, ?], F, Unit](DecCounter(name))

  def timer[F[_]: Applicative](name: String): ME[F, TimerData] =
    FreeT.liftF[MetricAction[F, ?], F, TimerData](StartTimer(name))

  def time[F[_]: Applicative](data: TimerData): ME[F, Unit] =
    FreeT.liftF[MetricAction[F, ?], F, Unit](StopTimer(data))

  def mark[F[_]: Applicative](name: String): ME[F, Unit] =
    FreeT.liftF[MetricAction[F, ?], F, Unit](Mark(name))

  def run[F[_]: Applicative, A](thunk: Eval[F[A]]): ME[F, A] =
    FreeT.liftF[MetricAction[F, ?], F, A](Run(thunk))

  def unit[F[_]: Applicative]: ME[F, Unit] =
    FreeT.pure(())

  def io[F[_]: Sync, A, M]
  (resources: M)
  (fa: MetricsEval1.ME[F, A])
  (implicit metrics: Metrics[F, M])
  : F[A] = {
    fa.foldMap(metrics.interpreter(resources))
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
