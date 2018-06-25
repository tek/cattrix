package chm

case class TimerData(name: String, start: Long)

sealed trait MetricAction[A]

case class IncCounter(name: String)
extends MetricAction[Unit]

case class DecCounter(name: String)
extends MetricAction[Unit]

case class StartTimer(name: String)
extends MetricAction[TimerData]

case class StopTimer(timer: TimerData)
extends MetricAction[Unit]

case class Mark(name: String)
extends MetricAction[Unit]
