# Cattrix - http and metrics with cats

A library for generic and abstract interaction with http requests and codahale metrics

Module IDs:

```sbt
"io.tryp" % "cattrix-http4s" % "0.1.4"
"io.tryp" % "cattrix-play" % "0.1.4"
"io.tryp" % "cattrix-metrics" % "0.1.4"
"io.tryp" % "cattrix-request" % "0.1.4"
"io.tryp" % "cattrix-codahale" % "0.1.4"
```

# Metrics

This module provides a DSL for monadically sequencing metrics around arbitrary computational thunks independent of the metrics reporter.

Take this example:

```scala
import cattrix.Metrics
import dbframework.DatabaseQuery

def databaseQuery: IO[DatabaseQuery] = ???

val prog: Metrics.Step[IO, DatabaseQuery] = for {
  t <- Metrics.timer("time")
  _ <- Metrics.incCounter("active")
  query <- Metrics.run(() => database.query("12345"))
  _ <- Metrics.decCounter("active")
  _ <- Metrics.time(t)
  _ <- Metrics.mark("success")
} yield query
```

This defines a program made out of `cats.free.FreeT`, where `Metrics.run` takes an arbitrary `F[_]: cats.effect.Sync`.
The `() =>` is for `Future` support (there is an instance of `Sync[Future]`, but you are strongly discouraged from using it).

The `FreeT` is designed around the algebra `Metric`, which currently consists of operations to increase/decrease a
counter, run a timer, and mark an event.

The program can be executed by conjuring up a codahale interpreter (or providing an alternative one manually) using the
typeclass `Metrics`. This is conveniently hidden by the helper `Metrics.compile`, which takes a data type description of
the intended interpreter, assuming that there is an instance of `Metrics` defined for it:

```scala
val prefix = "my.service"
val metric = "dbquery"
val io: IO[DatabaseQuery] = Metrics.compile(MetricTask(Codahale.as[IO](prefix), metric))(prog)
```

Instead of `Codahale`, you can pass `NoMetrics` or `CustomMetrics` to do something different with the metrics, or even
`Codahale(registry, prefix, customHandler)` to inspect the codahale interpreter's internals but do something else with
the data.

# Request

One major application intent of this library is to use it consistently across arbitrary web frameworks to meter http
requests.
This module provides the interface `Http`, which can use any backend client that implements an instance of
`HttpRequest` and wraps the requests in a metrics program similar to the one shown in the previous section.
`Http` can use custom backend-independent data types to represent requests and responses, but you can pass in the native
variants as well, if available.

An example request looks like this:

```scala
import cattrix.{Http, HttpConfig, Http4sRequest, Request, RequestTask, Response, Codahale, RequestMetric}

def http: Http[IO, http4s.Request[IO], http4s.Response[IO]] =
  Http.fromConfig(HttpConfig(Http4sRequest(), Codahale.as[IO]("my.service")))

val body = """{ "name": "tek" }"""
val request: Request = Request("post", "http://localhost/item", Some(body), None, Nil)
val response: IO[Response] = http.request(RequestTask(request, RequestMetric.named("postItem")))
```

The `Request` is translated into `http4s.Request` by the typeclass `HttpRequest`, while the class `HttpResponse` does
the opposite for `Response`.

`Http` contains a subinterface `NativeHttp`, with which you can pass in a `http4s.Response` and get back its native
counterpart:

```scala
val response: IO[http4s.Response[IO]] = http.native.get("http://localhost/item/1", "getItem")
```

This also showcases one of the convenience methods that both interfaces offer.

## JSON

`Http` provides methods for direct json decoding using implicit `io.circe.Decoder` instances:

```scala
import cattrix.JsonResponse
import io.circe.generic.auto._

case class Payload(data: String)

val io: IO[String] = http.getAs[Payload](url, metric).map {
  case JsonResponse.Successful(Payload(data)) => data
  case JsonResponse.Unsuccessful(status, body) => s"request failed with status $status: $body"
  case JsonResponse.DecodingError(error, body) => s"couldn't decode body: $error ($body)"
}
```

## Framework Support

Currently, typeclass instances for `http4s` and `Play` are available in the corresponding packages.
