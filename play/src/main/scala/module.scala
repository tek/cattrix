package chm

import javax.inject.{Inject, Singleton, Provider}

import scala.concurrent.{Future, ExecutionContext}

import cats.effect.IO
import play.api.Configuration
import play.api.libs.ws.WSClient
import com.google.inject.{TypeLiteral, AbstractModule}

import FutureInstances._

class FutureHttpModule
extends AbstractModule
{
  override def configure(): Unit = {
    bind(new TypeLiteral[Http[Future]](){}).toProvider(classOf[FutureHttpProvider])
    bind(new TypeLiteral[CodahaleInterpreter[Future]](){}).toInstance(NativeInterpreter[Future]())
  }
}

class FutureHttpProvider @Inject() (ws: WSClient, interpreter: CodahaleInterpreter[Future], config: Configuration)
(implicit ec: ExecutionContext)
extends Provider[Http[Future]]
{
  def get: Http[Future] = {
    val prefix = config.getOptional[String]("http.prefix").getOrElse("service")
    Http.fromConfig(HttpConfig(WsRequest[Future](ws), Codahale.withInterpreter(prefix, interpreter)))
  }
}

class IOHttpModule
extends AbstractModule
{
  override def configure(): Unit = {
    bind(new TypeLiteral[Http[IO]](){}).toProvider(classOf[IOHttpProvider])
    bind(new TypeLiteral[CodahaleInterpreter[IO]](){}).toInstance(NativeInterpreter[IO]())
  }
}

class IOHttpProvider @Inject() (ws: WSClient, interpreter: CodahaleInterpreter[IO], config: Configuration)
(implicit ec: ExecutionContext)
extends Provider[Http[IO]]
{
  def get: Http[IO] = {
    val prefix = config.getOptional[String]("http.prefix").getOrElse("service")
    Http.fromConfig(HttpConfig(WsRequest[IO](ws), Codahale.withInterpreter(prefix, interpreter)))
  }
}