package chm

import cats.Applicative

case class RequestMetric[F[_], In, Out](
  name: RequestTask[F, In, Out] => String,
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
    RequestMetric(_ => name, _ => err)
  }

  def named[F[_]: Applicative, In, Out](name: String): RequestMetric[F, In, Out] =
    strict(name, None)
}
