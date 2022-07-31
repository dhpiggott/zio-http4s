import org.http4s.*
import org.http4s.blaze.client.*
import org.http4s.blaze.server.*
import org.http4s.client.*
import org.http4s.client.dsl.*
import org.http4s.dsl.*
import org.http4s.server.*
import zio.*
import zio.blocking.*
import zio.clock.*
import zio.console.*
import zio.duration.*
import zio.interop.catz.*

object Blaze extends App with Http4sClientDsl[Task] with Http4sDsl[Task]:

  val totalPings = 10000
  val pingsAtATime = 1000

  override def run(
      args: List[String]
  ): URIO[Blocking & Clock & Console, ExitCode] =
    (
      for
        pending <- Ref.make(totalPings)
        _ <- RIO.foreachParN(pingsAtATime)(Range.inclusive(1, totalPings))(i =>
          for
            client <- ZIO.service[Client[Task]]
            server <- ZIO.service[Server]
            text <- client.get(server.baseUri / "ping")(_.as[String])
            _ = assert(text == "pong")
            pending <- pending.updateAndGet(_ - 1)
            width = totalPings.toString().length()
            fiberId <- ZIO.fiberId
            _ <- putStrLn(
              s"Ping %${width}s complete, %${width}s remaining, fiber %s"
                .format(i, pending, fiberId)
            )
          yield ()
        )
      yield ()
    )
      .provideSomeLayer[Blocking & Clock & Console](
        RIO
          .runtime[Blocking & Clock]
          .toManaged_
          .flatMap(implicit runtime =>
            BlazeClientBuilder[Task]
              .withExecutionContext(runtime.platform.executor.asEC)
              .withMaxTotalConnections(pingsAtATime)
              .withMaxConnectionsPerRequestKey(Function.const(pingsAtATime))
              .resource
              .toManagedZIO
          )
          .toLayer ++ RIO
          .runtime[Blocking & Clock]
          .toManaged_
          .flatMap(implicit runtime =>
            BlazeServerBuilder[Task]
              .withExecutionContext(runtime.platform.executor.asEC)
              .withMaxConnections(pingsAtATime)
              .withHttpApp(
                HttpRoutes
                  .of[Task] { case GET -> Root / "ping" => Ok("pong") }
                  .orNotFound
              )
              .resource
              .toManagedZIO
          )
          .toLayer
      )
      .exitCode
