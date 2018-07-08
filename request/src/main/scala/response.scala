package chm

import simulacrum.typeclass

case class Response(status: Int, body: String, headers: List[Header], cookies: List[Cookie])

object Response
{
  def ok(body: String): Response = Response(200, body, Nil, Nil)

  def notFound(body: String): Response = Response(404, body, Nil, Nil)

  def err(body: String): Response = Response(503, body, Nil, Nil)

  def header(name: String)(response: Response): Option[List[String]] =
    response.headers.find(_.name == name).map(_.values)

  def header1(name: String)(response: Response): Option[String] =
    for {
      values <- header(name)(response)
      value <- values.headOption
    } yield value
}

@typeclass
trait HttpResponse[A]
{
  def status(a: A): Int
}

object HttpResponse
{
  implicit def HttpResponse_Response: HttpResponse[Response] =
    new HttpResponse[Response] {
      def status(a: Response): Int = a.status
    }
}
