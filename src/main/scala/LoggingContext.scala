import cats.effect.{IO, IOLocal}
import com.typesafe.scalalogging.{CanLog, Logger}
import sttp.model.Header
import sttp.tapir.server.interceptor._

object LoggingContext {
  // use lazy val to avoid blocking of `io-compute` threads on loading of `LoggingContext$` class by JVM
  private[this] val local = IOLocal(empty).unsafeRunSync()(cats.effect.unsafe.IORuntime.global)

  def get: IO[LoggingContext] = local.get

  def set(v: LoggingContext): IO[Unit] = local.set(v)

  def use[A](f: LoggingContext => IO[A]): IO[A] = local.get.flatMap(f)

  def empty: LoggingContext = LoggingContext(CorrelationId(None), Map.empty)

  implicit case object CanLogLoggingContext extends CanLog[LoggingContext] {
    override def logMessage(originalMsg: String, ctx: LoggingContext): String = {
      val sb = new java.lang.StringBuilder(originalMsg.length + 32)
      sb.append("[cid=").append(ctx.cid.value.getOrElse("unset"))
      ctx.fields.foreach { case (k, v) =>
        sb.append(',').append(k).append('=').append(v)
      }
      sb.append("] ").append(originalMsg).toString
    }
  }
}

case class LoggingContext(cid: CorrelationId, fields: Map[String, String])

/** Tapir interceptor which extracts correlation ID from the request headers and puts correlation ID into Http response header. If
  * correlation ID is not present in the HTTP header, a new one is generated.
  */
object LoggingContextInterceptor extends RequestInterceptor[IO] {
  private val log = Logger[this.type]

  override def apply[R, B](
      responder: Responder[IO, B],
      requestHandler: EndpointInterceptor[IO] => RequestHandler[IO, R, B]
  ): RequestHandler[IO, R, B] =
    RequestHandler.from { case (request, endpoints, monad) =>
      val cid = request.header(CorrelationId.headerName).getOrElse(CorrelationId.generate)
      log.info(s"$request, $endpoints")
      for {
        _ <- LoggingContext.set(LoggingContext(CorrelationId(Some(cid)), Map.empty))
        requestResult <- requestHandler(EndpointInterceptor.noop).apply(request, endpoints)(monad)
      } yield requestResult match {
        case x: RequestResult.Response[B] =>
          val r = x.response
          val rr = RequestResult.Response(r.copy(headers = r.headers :+ Header(CorrelationId.headerName, cid)))
          log.info(s"response: $rr")
          rr
        case x =>
          log.info(s"x: $x")
          x
      }
    }
}
