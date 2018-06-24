package sf

import cats.effect.Sync
import cats.Monad
import cats.data.Kleisli
import cats.syntax.all._
import com.codahale.metrics.{MetricRegistry, Counter, Meter, Timer, SharedMetricRegistries}

import Codahale._

sealed trait MetricAction

case class IncCounter(name: String)
extends MetricAction

case class DecCounter(name: String)
extends MetricAction

object CHMetricsOps
{
  def metricName(path: String, name: String): String =
    MetricRegistry.name(path, name)

  type RegistryCons[A] = String => MetricRegistry => A

  def flip[A](cons: MetricRegistry => String => A): RegistryCons[A] = prefix => registry => cons(registry)(prefix)

  val consCounter: RegistryCons[Counter] = flip(_.counter)
  val consMeter: RegistryCons[Meter] = flip(_.meter)
  val consTimer: RegistryCons[Timer] = flip(_.timer)
}
import CHMetricsOps._

object ExecCHMetric
{
  def exec[F[_]: Sync, A](op: MetricRegistry => A)(metrics: Codahale): F[A] = {
    val registry = SharedMetricRegistries.getOrCreate(metrics.registry)
    Sync[F].delay(op(registry))
  }

  def consMetric[F[_]: Sync, A](cons: RegistryCons[A]): String => CHMetricsOp[F, A] =
    name => Kleisli(metrics => exec(cons(metricName(metrics.prefix, name)))(metrics))

  def metricOp[F[_]: Sync, A, B](cons: RegistryCons[B])(f: B => A)(name: String): CHMetricsOp[F, A] =
    for {
      c <- consMetric(cons).apply(name)
      a <- CHMetricsOp.delay(f(c))
    } yield a

  def counterOp[F[_]: Sync, A]: (Counter => A) => String => CHMetricsOp[F, A] =
    metricOp(consCounter) _

  def compile[F[_]: Sync: Monad](action: MetricAction): CHMetricsOp[F, Unit] = {
    val cons = action match {
      case IncCounter(name) =>
        counterOp.apply(_.inc())(name)
      case DecCounter(name) =>
        counterOp.apply(_.dec())(name)
    }
    cons
  }
}
