import org.http4s.*
import org.http4s.client.*
import org.http4s.client.dsl.*
import org.http4s.dsl.*
import org.http4s.ember.client.*
import org.http4s.ember.server.*
import org.http4s.server.*
import zio.*
import zio.blocking.*
import zio.clock.*
import zio.console.*
import zio.duration.*
import zio.interop.catz.*

object Ember extends App with Http4sClientDsl[Task] with Http4sDsl[Task]:

  override def run(
      args: List[String]
  ): URIO[Blocking & Clock & Console, ExitCode] =
    (
      for
        n <- UIO(1000)
        pending <- Ref.make(n)
        _ <- RIO.foreachPar(Range.inclusive(1, n))(i =>
          for
            client <- ZIO.service[Client[Task]]
            server <- ZIO.service[Server]
            text <- client.get(server.baseUri / "ping")(_.as[String])
            _ = assert(text == "pong")
            pending <- pending.updateAndGet(_ - 1)
            width = n.toString().length()
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
            EmberClientBuilder
              .default[Task]
              .withMaxTotal(1000)
              .withMaxPerKey(Function.const(1000))
              .build
              .toManagedZIO
          )
          .toLayer ++ RIO
          .runtime[Blocking & Clock]
          .toManaged_
          .flatMap(implicit runtime =>
            EmberServerBuilder
              .default[Task]
              .withMaxConnections(1000)
              .withHttpApp(
                HttpRoutes
                  .of[Task] { case GET -> Root / "ping" => Ok("pong") }
                  .orNotFound
              )
              .build
              .toManagedZIO
          )
          .toLayer
      )
      .exitCode
