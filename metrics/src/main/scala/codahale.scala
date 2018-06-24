package sf

import cats.data.Kleisli
import cats.effect.Sync
import com.codahale.metrics.MetricRegistry

import Codahale.CHMetricsOp

case class Codahale(registry: String, prefix: String)

object Codahale
{
  implicit def Metrics_CHMetrics: Metrics[Codahale] =
    new Metrics[Codahale] {
      def run[F[_]: Sync](metrics: Codahale)(metric: MetricAction): F[Unit] =
        ExecCHMetric.compile(metric).run(metrics)
    }

  type CHMetricsOp[F[_], A] = Kleisli[F, Codahale, A]

  def as(prefix: String): Codahale = Codahale("default", prefix)
}

object CHMetricsOp
{
  def lift[F[_]: Sync, A](f: MetricRegistry => A): CHMetricsOp[F, A] =
    Kleisli(ExecCHMetric.exec[F, A](f))

  def delay[F[_]: Sync, A](f: => A): CHMetricsOp[F, A] = lift(reg => f)
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
