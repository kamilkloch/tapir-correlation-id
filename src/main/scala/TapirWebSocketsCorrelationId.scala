import cats.effect.{IO, IOApp}
import com.typesafe.scalalogging.Logger
import fs2.Stream
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3._
import sttp.client3.asynchttpclient.fs2.AsyncHttpClientFs2Backend
import sttp.client3.httpclient.fs2.HttpClientFs2Backend
import sttp.model.{Header, ResponseMetadata}
import sttp.tapir._
import sttp.tapir.client.sttp.SttpClientInterpreter
import sttp.tapir.client.sttp.ws.fs2._
import sttp.tapir.server.http4s.{Http4sServerInterpreter, Http4sServerOptions}
import sttp.ws.WebSocket

object TapirWebSocketsCorrelationId extends IOApp.Simple {
  private val log = Logger.takingImplicit[this.type, LoggingContext]

  private val wsEndpoint = endpoint.get
    .in("ping")
    .out(webSocketBody[Int, CodecFormat.TextPlain, Int, CodecFormat.TextPlain](Fs2Streams[IO]))

  private val serverOptions = Http4sServerOptions
    .customiseInterceptors[IO]
    .prependInterceptor(LoggingContextInterceptor)
    .serverLog(None)
    .options

  private val wsRoutes = Http4sServerInterpreter[IO](serverOptions)
    .toWebSocketRoutes(wsEndpoint.serverLogicSuccess(_ => IO.pure((x: Stream[IO, Int]) => x)))

  private val interpreter = new SttpClientInterpreterWithRandomCorrelationId(SttpClientInterpreter())

  def makeWsClient(wsLogic: (WebSocket[IO], ResponseMetadata) => IO[Unit]): IO[Unit] = {
    AsyncHttpClientFs2Backend.resource[IO]().use { backend =>
      IO(CorrelationId.generate).flatMap { cid =>
        log.info("sttp ws request")(LoggingContext(CorrelationId(Some(cid)), Map.empty))
        basicRequest
          .headers(Header(CorrelationId.headerName, cid))
          .response(asWebSocketAlwaysWithMetadata(wsLogic))
          .get(uri"ws://localhost:8081/ping")
          .send(backend)
          .void
      }
    }
  }

  private val sttpWsClient = makeWsClient { case (ws, responseMetadata) =>
    val cid = CorrelationId(responseMetadata.header(CorrelationId.headerName))
    implicit val ctx: LoggingContext = LoggingContext(cid, Map.empty)
    IO(log.info("Sending 1")) >>
      ws.sendText("1") >>
      ws.receiveText().flatMap(s => IO(log.info(s"Received $s")))
  }

  private val tapirWsClient = HttpClientFs2Backend.resource[IO]().use { backend =>
    interpreter
      .toClientThrowErrors(wsEndpoint, Some(uri"ws://localhost:8081"), backend)
      .apply(())
      .flatMap { case ResponseWithContext(ctx, wsPipe) =>
        implicit val loggingContext: LoggingContext = ctx
        Stream(1)
          .covary[IO]
          .evalTap(x => IO(log.info(s"Sending $x")))
          .through(wsPipe)
          .evalTap(x => IO(log.info(s"Received $x")))
          .compile
          .drain
      }
  }

  def run: IO[Unit] = {
    BlazeServerBuilder[IO]
      .bindHttp(8081, "localhost")
      .withHttpWebSocketApp(wsb => Router("/" -> wsRoutes(wsb)).orNotFound)
      .resource
      .use { _ =>
        IO(log.info("\n\n***** sttp websocket client")(LoggingContext.empty)) >>
          sttpWsClient >>
          IO(log.info("\n\n***** tapir websocket client")(LoggingContext.empty)) >>
          tapirWsClient
      }
  }
}
