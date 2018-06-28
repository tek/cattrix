package chm

import cats.Eval

case class TimerData(name: String, start: Long)

sealed trait Metric[F[_], A]

case class IncCounter[F[_]](name: String)
extends Metric[F, Unit]

case class DecCounter[F[_]](name: String)
extends Metric[F, Unit]

case class StartTimer[F[_]](name: String)
extends Metric[F, TimerData]

case class StopTimer[F[_]](timer: TimerData)
extends Metric[F, Unit]

case class Mark[F[_]](name: String)
extends Metric[F, Unit]

case class Run[F[_], A](thunk: Eval[F[A]])
extends Metric[F, A]
