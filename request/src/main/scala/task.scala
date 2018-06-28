package chm

import cats.Applicative

case class RequestTask[F[_]](
  request: Request,
  metric: RequestMetric[F],
  logRequest: Boolean,
  logClientError: Response => Option[LogMessage],
  logServerError: Response => Option[LogMessage],
  logFatal: String => Option[LogMessage],
)

object RequestTask
{
  def metric[F[_]: Applicative](request: Request, metric: String): RequestTask[F] =
    RequestTask(request, RequestMetric.strict[F](metric, None), false, a => None, a => None, a => None)
}
