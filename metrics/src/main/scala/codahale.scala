package chm

import cats.Monad
import cats.data.Kleisli
import cats.effect.Sync
import com.codahale.metrics.{MetricRegistry, Counter, Meter, Timer, SharedMetricRegistries}

import Codahale.CodahaleOp
import CodahaleOps._

case class Codahale(registry: String, prefix: String, interpreter: Codahale.Interpreter)

object Codahale
{
  sealed trait Interpreter

  case object NativeInterpreter
  extends Interpreter

  case class CustomInterpreter(handler: Codahale => MetricAction => Unit)
  extends Interpreter

  implicit def Metrics_CHMetrics: Metrics[Codahale] =
    new Metrics[Codahale] {
      def run[F[_]: Sync](metrics: Codahale)(metric: MetricAction): F[Unit] =
        metrics.interpreter match {
          case NativeInterpreter => ExecCodahale.compile(metric).run(metrics)
          case CustomInterpreter(handler) => Sync[F].delay(handler(metrics)(metric))
        }
    }

  type CodahaleOp[F[_], A] = Kleisli[F, Codahale, A]

  def withInterpreter(prefix: String, interpreter: Interpreter): Codahale = Codahale("default", prefix, interpreter)

  def as(prefix: String): Codahale = withInterpreter(prefix, NativeInterpreter)
}

object CodahaleOp
{
  def lift[F[_]: Sync, A](f: MetricRegistry => A): CodahaleOp[F, A] =
    Kleisli(ExecCodahale.exec[F, A](f))

  def delay[F[_]: Sync, A](f: => A): CodahaleOp[F, A] = lift(reg => f)
}

object CodahaleOps
{
  def metricName(path: String, name: String): String =
    MetricRegistry.name(path, name)

  type RegistryCons[A] = String => MetricRegistry => A

  def flip[A](cons: MetricRegistry => String => A): RegistryCons[A] = prefix => registry => cons(registry)(prefix)

  val consCounter: RegistryCons[Counter] = flip(_.counter)
  val consMeter: RegistryCons[Meter] = flip(_.meter)
  val consTimer: RegistryCons[Timer] = flip(_.timer)
}

object ExecCodahale
{
  def exec[F[_]: Sync, A](op: MetricRegistry => A)(metrics: Codahale): F[A] = {
    val registry = SharedMetricRegistries.getOrCreate(metrics.registry)
    Sync[F].delay(op(registry))
  }

  def consMetric[F[_]: Sync, A](cons: RegistryCons[A]): String => CodahaleOp[F, A] =
    name => Kleisli(metrics => exec(cons(metricName(metrics.prefix, name)))(metrics))

  def metricOp[F[_]: Sync, A, B](cons: RegistryCons[B])(f: B => A)(name: String): CodahaleOp[F, A] =
    for {
      c <- consMetric(cons).apply(name)
      a <- CodahaleOp.delay(f(c))
    } yield a

  def counterOp[F[_]: Sync, A]: (Counter => A) => String => CodahaleOp[F, A] =
    metricOp(consCounter) _

  def compile[F[_]: Sync: Monad](action: MetricAction): CodahaleOp[F, Unit] = {
    val cons = action match {
      case IncCounter(name) =>
        counterOp.apply(_.inc())(name)
      case DecCounter(name) =>
        counterOp.apply(_.dec())(name)
    }
    cons
  }
}

// case class MetricData(name: String, timer: Timer.Context)

// object Metrics
// {
//   def metricName(path: String, name: String): String =
//     MetricRegistry.name(path, name)

//   def requestTimer(name: String)(registry: MetricRegistry)(implicit ec: ExecutionContext): Future[Timer] =
//     Future(registry.timer(metricName(name, "requestTimer")))

//   def counter(name: String, metric: String)(registry: MetricRegistry)(implicit ec: ExecutionContext): Future[Counter] =
//     Future(registry.counter(metricName(name, metric)))

//   def inc(name: String, metric: String)(registry: MetricRegistry)(implicit ec: ExecutionContext): Future[Unit] =
//     for {
//       c <- counter(name, metric)(registry)
//       _ <- Future(c.inc())
//     } yield ()

//   def dec(name: String, metric: String)(registry: MetricRegistry)(implicit ec: ExecutionContext): Future[Unit] =
//     for {
//       c <- counter(name, metric)(registry)
//       _ <- Future(c.dec())
//     } yield ()

//   def failures(name: String)(registry: MetricRegistry)(implicit ec: ExecutionContext): Future[Counter] =
//     counter(name, "failure")(registry)

//   def meter(name: String, metric: String)(registry: MetricRegistry)(implicit ec: ExecutionContext): Future[Meter] =
//     Future(registry.meter(metricName(name, metric)))

//   def mark(name: String, metric: String)(registry: MetricRegistry)(implicit ec: ExecutionContext): Future[Unit] =
//     for {
//       c <- meter(name, metric)(registry)
//       _ <- Future(c.mark())
//     } yield ()

//   def statusCode(name: String)(code: Int)(registry: MetricRegistry)(implicit ec: ExecutionContext): Future[Unit] =
//     mark(name, code.toString)(registry)
// }
