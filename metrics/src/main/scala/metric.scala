package chm

sealed trait MetricAction

case class IncCounter(name: String)
extends MetricAction

case class DecCounter(name: String)
extends MetricAction
