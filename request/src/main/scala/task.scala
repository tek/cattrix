package chm

import cats.Applicative

case class RequestTask[F[_], In, Out](
  request: In,
  metric: RequestMetric[F, In, Out],
)

object RequestTask
{
  def metric[F[_]: Applicative, In, Out](request: In, metric: String): RequestTask[F, In, Out] =
    RequestTask(request, RequestMetric.strict[F, In, Out](metric, None))
}
