package sf

import cats.Functor
import cats.syntax.functor._
import cats.effect.{IO, LiftIO, Sync}
import play.api.libs.ws.{WSAuthScheme, WSClient, WSResponse}

case class WsHttp[F[_]](client: WSClient)

object WsHttp
{
  implicit def HttpRequest_WsHttp[F[_]: Sync: LiftIO]: HttpRequest[F, WsHttp] =
    new HttpRequest[F, WsHttp] {
      def execute(resources: WsHttp[F])(request: Request): F[Either[String, Response]] =
        WsHttp.execute[F](resources.client)(request)
    }

  def responseCookies(rs: WSResponse): List[Cookie] = rs.cookies.map(a => Cookie(a.name, a.value)).toList

  def responseHeaders(rs: WSResponse): List[Header] = rs.headers.toList.map { case (k, v) => Header(k, v.toList) }

  def execute[F[_]: LiftIO: Functor](client: WSClient)(request: Request)
  : F[Either[String, Response]] = {
    val req = client.url(request.url)
    val authed = request.auth
      .map(auth => req.withAuth(auth.user, auth.password, WSAuthScheme.BASIC))
      .getOrElse(req)
    val headered = request.headers
      .foldLeft(authed)((z, a) => a.values.foldLeft(z)((z0, a0) => z0.addHttpHeaders(a.name -> a0)))
    val bodied = request.body
      .map(headered.withBody[String])
      .getOrElse(headered)
    LiftIO[F].liftIO(IO.fromFuture(IO(bodied.execute(request.method.toUpperCase))))
      .map { rs =>
        println(rs)
        println(rs.body)
        Right(Response(rs.status, rs.body, responseHeaders(rs), responseCookies(rs)))
      }
  }
}
