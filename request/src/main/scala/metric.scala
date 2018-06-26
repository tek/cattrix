package chm

sealed trait RequestMetric

case class StaticRequestMetric(name: String, error: Option[String])
extends RequestMetric

case class DynamicRequestMetric(
  name: RequestTask => String,
  error: Response => Option[String],
)
extends RequestMetric