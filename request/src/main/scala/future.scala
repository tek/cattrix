package chm

import scala.concurrent.{Future, ExecutionContext}

import cats.instances.future.catsStdInstancesForFuture
import cats.effect.{Sync, ExitCase, LiftIO, IO}

object FutureInstances
{
  implicit def Sync_Future(implicit ec: ExecutionContext): Sync[Future] = {
    val monad = catsStdInstancesForFuture
    new Sync[Future] {
      def suspend[A](thunk: => Future[A]): Future[A] = thunk

      def pure[A](a: A): Future[A] = monad.pure(a)

      def raiseError[A](e: Throwable): Future[A] = monad.raiseError(e)

      def handleErrorWith[A](fa: Future[A])(f: Throwable => Future[A]): Future[A] =
        monad.handleErrorWith(fa)(f)

      def bracketCase[A, B]
      (acquire: Future[A])
      (use: A => Future[B])
      (release: (A, ExitCase[Throwable]) => Future[Unit]): Future[B] =
        for {
        a <- acquire
        etb <- attempt(use(a))
        _ <- release(a, etb match {
          case Left(e) => ExitCase.error[Throwable](e)
          case Right(_) => ExitCase.complete
        })
        b <- rethrow(pure(etb))
      } yield b

      def flatMap[A, B](fa: Future[A])(f: A => Future[B]): Future[B] = monad.flatMap(fa)(f)

      def tailRecM[A, B](a: A)(f: A => Future[Either[A,B]]): Future[B] = monad.tailRecM(a)(f)
    }
  }

  implicit def LiftIO_Future(implicit ec: ExecutionContext): LiftIO[Future] =
    new LiftIO[Future] {
      def liftIO[A](ioa: IO[A]): Future[A] = Future(ioa.unsafeRunSync)
    }
}
