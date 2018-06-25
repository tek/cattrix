package chm

import java.util.concurrent.TimeUnit

import cats.{Monad, ~>}
import cats.data.Kleisli
import cats.effect.Sync
import com.codahale.metrics.{MetricRegistry, Counter, Meter, Timer, SharedMetricRegistries, Histogram}

import Codahale.CodahaleOp
import CodahaleOps._

sealed trait CodahaleInterpreter[F[_]]

case class NativeInterpreter[F[_]]()
extends CodahaleInterpreter[F]

case class CustomInterpreter[F[_]](handler: Codahale[F] => (MetricAction ~> F))
extends CodahaleInterpreter[F]

case class Codahale[F[_]](registry: String, prefix: String, interpreter: CodahaleInterpreter[F])

object Codahale
extends CodahaleInstances
with CodahaleFunctions
{
  type CodahaleOp[F[_], A] = Kleisli[F, Codahale[F], A]
}

object CodahaleOp
{
  type RegistryCons[A] = String => MetricRegistry => A

  def exec[F[_]: Sync, A](op: MetricRegistry => A)(metrics: Codahale[F]): F[A] = {
    val registry = SharedMetricRegistries.getOrCreate(metrics.registry)
    Sync[F].delay(op(registry))
  }

  def consMetric[F[_]: Sync, A](cons: RegistryCons[A]): String => CodahaleOp[F, A] =
    name => Kleisli(metrics => exec(cons(metricName(metrics.prefix, name)))(metrics))

  def lift[F[_]: Sync, A](f: MetricRegistry => A): CodahaleOp[F, A] =
    Kleisli(exec[F, A](f))

  def delay[F[_]: Sync, A](f: => A): CodahaleOp[F, A] = lift(reg => f)

  def metricName(path: String, name: String): String =
    MetricRegistry.name(path, name)

  def flip[A](cons: MetricRegistry => String => A): RegistryCons[A] = prefix => registry => cons(registry)(prefix)

  def metric[F[_]: Sync, A, B](cons: MetricRegistry => String => B)(f: B => A)(name: String): CodahaleOp[F, A] =
    for {
      c <- consMetric(flip(cons)).apply(name)
      a <- delay(f(c))
    } yield a
}

object CodahaleOps
{
  def counter[F[_]: Sync, A]: (Counter => A) => String => CodahaleOp[F, A] =
    CodahaleOp.metric(_.counter) _

  def meter[F[_]: Sync, A]: (Meter => A) => String => CodahaleOp[F, A] =
    CodahaleOp.metric(_.meter) _

  def timer[F[_]: Sync, A]: (Timer => A) => String => CodahaleOp[F, A] =
    CodahaleOp.metric(_.timer) _

  def histogram[F[_]: Sync, A]: (Histogram => A) => String => CodahaleOp[F, A] =
    CodahaleOp.metric(_.histogram) _
}

trait CodahaleInstances
{
  implicit def Metrics_CHMetrics[F[_]: Sync]: Metrics[F, Codahale[F]] =
    new Metrics[F, Codahale[F]] {
      def run[A](metrics: Codahale[F])(metric: MetricAction[A]): F[A] =
        metrics.interpreter match {
          case NativeInterpreter() => NativeInterpreter.compile.apply(metric).run(metrics)
          case CustomInterpreter(handler) => handler(metrics)(metric)
        }
    }
}

trait CodahaleFunctions
{
  def withInterpreter[F[_]](prefix: String, interpreter: CodahaleInterpreter[F]): Codahale[F] =
    Codahale("default", prefix, interpreter)

  def as[F[_]](prefix: String): Codahale[F] = withInterpreter(prefix, NativeInterpreter())
}

object NativeInterpreter
{
  def compile[F[_]: Sync]: MetricAction ~> CodahaleOp[F, ?] =
    new (MetricAction ~> CodahaleOp[F, ?]) {
      def apply[A](action: MetricAction[A]): CodahaleOp[F, A] = {
        action match {
          case IncCounter(name) =>
            counter.apply(_.inc())(name)
          case DecCounter(name) =>
            counter.apply(_.dec())(name)
          case StartTimer(name) =>
            for {
              start <- CodahaleOp.delay(System.nanoTime())
            } yield TimerData(name, start)
          case StopTimer(data) =>
            for {
              stop <- CodahaleOp.delay(System.nanoTime())
              _ <- timer.apply(_.update(stop - data.start, TimeUnit.NANOSECONDS))(data.name)
            } yield ()
          case Mark(name) =>
            meter.apply(_.mark())(name)
        }
      }
    }
}
