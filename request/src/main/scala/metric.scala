package cattrix

import cats.{Applicative, FlatMap}
import cats.syntax.flatMap._
import cats.syntax.functor._

case class RequestMetric[F[_], In, Out](
  name: In => F[String],
  error: Out => Metrics.Step[F, Unit],
)

object RequestMetric
{
  def strict[F[_]: Applicative, In, Out](name: String, error: Option[String]): RequestMetric[F, In, Out] = {
    val err = error match {
      case Some(errName) =>
        Metrics.mark[F](errName)
      case None =>
        Metrics.unit
    }
    RequestMetric(_ => Applicative[F].pure(name), _ => err)
  }

  def named[F[_]: Applicative, In, Out](name: String): RequestMetric[F, In, Out] =
    strict(name, None)

  def contramapIn[F[_]: FlatMap, In0, In1, Out]
  (metric: RequestMetric[F, In0, Out])
  (f: In1 => F[In0])
  : RequestMetric[F, In1, Out] = {
    def name(in1: In1): F[String] =
      for {
        in0 <- f(in1)
        n <- metric.name(in0)
      } yield n
    RequestMetric(name, metric.error)
  }
}
