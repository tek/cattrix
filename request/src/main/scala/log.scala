package sf

sealed trait LogLevel

object LogLevel
{
  case object Debug
  extends LogLevel

  case object Info
  extends LogLevel

  case object Warn
  extends LogLevel

  case object Error
  extends LogLevel
}

case class LogMessage(lines: List[String], level: LogLevel)
