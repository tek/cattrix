package chm

import javax.inject.Inject

import scala.concurrent.{Future, ExecutionContext}
import scala.collection.mutable.ArrayBuffer

import org.specs2.Specification
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

class MetricsSpec
extends Specification
{
  def is = s2"""
  http $http
  """

  val payload = "ok!"

  val ws = MockWS {
    case a => Action(Results.Ok(payload))
  }

  val metricsLog: ArrayBuffer[MetricAction] = ArrayBuffer()

  val coda = Codahale.CustomInterpreter(metrics => metricsLog.append(_))

  val application: Application =
    GuiceApplicationBuilder()
      .bindings(new FutureHttpModule)
      .overrides(bind[WSClient].to(ws), bind[Codahale.Interpreter].to(coda))
      .build()

  def http = new WithApplication(application) {
    val ctrl = app.injector.instanceOf[Ctrl]
    val result = ctrl.act()(FakeRequest())
    metricsLog.toList.must_==(List(IncCounter("activeRequests"), DecCounter("activeRequests")))
      .and(contentAsString(result).must_==(payload))
  }
}
