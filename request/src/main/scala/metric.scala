package chm

import cats.Applicative

case class RequestMetric[F[_]](
  name: RequestTask[F] => String,
  error: Response => Metrics.Step[F, Unit],
)

object RequestMetric
{
  def strict[F[_]: Applicative](name: String, error: Option[String]): RequestMetric[F] = {
    val err = error match {
      case Some(errName) =>
        Metrics.mark[F](errName)
      case None =>
        Metrics.unit
    }
    RequestMetric(_ => name, _ => err)
  }

  def named[F[_]: Applicative](name: String): RequestMetric[F] =
    strict(name, None)
}
