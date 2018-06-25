package chm

import javax.inject.Inject

import scala.concurrent.{Future, ExecutionContext}
import scala.collection.mutable.ArrayBuffer

import cats.~>
import org.specs2.Specification
import com.google.inject.{TypeLiteral, AbstractModule}
import play.api.Application
import play.api.mvc.{AbstractController, ControllerComponents, Results}
import play.api.inject.bind
import play.api.libs.ws.WSClient
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test._
import play.api.test.Helpers._
import mockws.MockWS
import mockws.MockWSHelpers.Action

import FutureInstances._

class Ctrl @Inject() (components: ControllerComponents, http: Http[Future])
(implicit ec: ExecutionContext)
extends AbstractController(components)
{
  def act = Action.async {
    http.get("url", "met").map {
      case Right(response) => Ok(response.body)
      case Left(error) => NotFound(error)
    }
  }
}

class MetricsSpec(implicit ec: ExecutionContext)
extends PlaySpecification
{
  val payload = "ok!"

  val ws = MockWS {
    case a => Action(Results.Ok(payload))
  }

  val metricsLog: ArrayBuffer[MetricAction[_]] = ArrayBuffer()

  def interpreter(resources: Codahale[Future]): MetricAction ~> Future =
    new (MetricAction ~> Future) {
      def apply[A](action: MetricAction[A]): Future[A] = {
        metricsLog.append(action)
        NoMetrics.interpreter[Future, A](action)
      }
    }

  val coda = CustomInterpreter[Future](interpreter)

  val mod = new AbstractModule {
    override def configure(): Unit = {
      bind(new TypeLiteral[CodahaleInterpreter[Future]](){}).toInstance(coda)
    }
  }

  val application: Application =
    GuiceApplicationBuilder()
      .bindings(new FutureHttpModule)
      .overrides(mod)
      .overrides(bind[WSClient].to(ws))
      .build()

  def http = new WithApplication(application) {
    val ctrl = app.injector.instanceOf[Ctrl]
    val result = ctrl.act()(FakeRequest())
    contentAsString(result).must_==(payload)
      .and(metricsLog.toList.must_==(List(
        StartTimer("requestTimer"),
        IncCounter("activeRequests"),
        DecCounter("activeRequests"),
        StopTimer(TimerData("requestTimer", 0)),
        Mark("200"),
      )))
  }

  "inject a logging codahale interpreter" >> http
}
