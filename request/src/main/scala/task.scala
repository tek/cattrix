package chm

case class RequestTask(
  request: Request,
  metric: RequestMetric,
  logRequest: Boolean,
  logClientError: Response => Option[LogMessage],
  logServerError: Response => Option[LogMessage],
  logFatal: String => Option[LogMessage],
)

object RequestTask
{
  def metric(request: Request, metric: String): RequestTask =
    RequestTask(request, StaticRequestMetric(metric, None), false, a => None, a => None, a => None)
}
