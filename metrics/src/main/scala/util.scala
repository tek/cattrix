package cattrix

import cats.syntax.either._

import cats.ApplicativeError

object Fatal
{
  def fromEither[F[_], A](e: Either[String, A])(implicit AE: ApplicativeError[F, Throwable]): F[A] =
    ApplicativeError[F, Throwable].fromEither(e.leftMap(new Exception(_)))
}
