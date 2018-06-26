package chm

import cats.Eval

case class TimerData(name: String, start: Long)

sealed trait MetricAction[F[_], A]

case class IncCounter[F[_]](name: String)
extends MetricAction[F, Unit]

case class DecCounter[F[_]](name: String)
extends MetricAction[F, Unit]

case class StartTimer[F[_]](name: String)
extends MetricAction[F, TimerData]

case class StopTimer[F[_]](timer: TimerData)
extends MetricAction[F, Unit]

case class Mark[F[_]](name: String)
extends MetricAction[F, Unit]

case class Run[F[_], A](thunk: Eval[F[A]])
extends MetricAction[F, A]
