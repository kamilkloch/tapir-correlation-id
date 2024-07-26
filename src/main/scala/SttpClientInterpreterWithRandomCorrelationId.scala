import com.typesafe.scalalogging.Logger
import sttp.client3.{Request, Response, SttpBackend}
import sttp.model.Uri
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.PublicEndpoint
import sttp.tapir.client.sttp.{SttpClientInterpreter, WebSocketToPipe}

class SttpClientInterpreterWithRandomCorrelationId(underlying: SttpClientInterpreter) {
  private[this] val log = Logger.takingImplicit[this.type, LoggingContext]

  private def addCorrelationIdHeader[O, R](cid: String)(req: Request[O, R]): Request[O, R] =
    req.header(CorrelationId.headerName, cid)

  def toClientThrowErrors[F[_], I, E, O, R](e: PublicEndpoint[I, E, O, R], baseUri: Option[Uri], backend: SttpBackend[F, R])(implicit
      wsToPipe: WebSocketToPipe[R]
  ): I => F[ResponseWithContext[O]] = {
    implicit val me: MonadError[F] = backend.responseMonad

    def req(cid: String): I => Request[O, R] =
      underlying.toRequestThrowErrors(e, baseUri) andThen addCorrelationIdHeader(cid)

    (i: I) =>
      me.eval(CorrelationId.generate).flatMap { cid =>
        log.info("tapir ws request")(LoggingContext(CorrelationId(Some(cid)), Map.empty))
        backend.send(req(cid)(i)).map { response: Response[O] =>
          val ctx = LoggingContext(CorrelationId(response.header(CorrelationId.headerName)), Map.empty)
          log.info(s"${response}")(ctx)
          ResponseWithContext(ctx, response.body)
        }
      }
  }
}
