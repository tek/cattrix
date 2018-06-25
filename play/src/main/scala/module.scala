package chm

import javax.inject.{Inject, Singleton, Provider}

import scala.concurrent.{Future, ExecutionContext}

import cats.effect.IO
import play.api.Configuration
import play.api.inject.{SimpleModule, bind}
import play.api.libs.ws.WSClient
import com.google.inject.{TypeLiteral, AbstractModule}

import FutureInstances._

class FutureHttpModule
extends AbstractModule
{
  override def configure(): Unit = {
    bind(new TypeLiteral[Http[Future]](){}).toProvider(classOf[FutureHttpProvider])
    bind(classOf[Codahale.Interpreter]).toInstance(Codahale.NativeInterpreter)
  }
}

class FutureHttpProvider @Inject() (ws: WSClient, reporter: Codahale.Interpreter, config: Configuration)
(implicit ec: ExecutionContext)
extends Provider[Http[Future]]
{
  def get: Http[Future] = {
    val prefix = config.getOptional[String]("http.prefix").getOrElse("service")
    Http.fromConfig(HttpConfig(WsRequest[Future](ws), Codahale.withInterpreter(prefix, reporter)))
  }
}

class IOHttpModule
extends SimpleModule(bind[Http[IO]].toProvider[IOHttpProvider])

class IOHttpProvider @Inject() (ws: WSClient, reporter: Codahale.Interpreter, config: Configuration)
(implicit ec: ExecutionContext)
extends Provider[Http[IO]]
{
  def get: Http[IO] = {
    val prefix = config.getOptional[String]("http.prefix").getOrElse("service")
    Http.fromConfig(HttpConfig(WsRequest[IO](ws), Codahale.withInterpreter(prefix, reporter)))
  }
}
