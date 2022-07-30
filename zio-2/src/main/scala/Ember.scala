import org.http4s.*
import org.http4s.client.*
import org.http4s.client.dsl.*
import org.http4s.dsl.*
import org.http4s.ember.client.*
import org.http4s.ember.server.*
import org.http4s.server.*
import zio.*
import zio.interop.catz.*

object Ember extends ZIOApp with Http4sClientDsl[Task] with Http4sDsl[Task]:

  type Environment = Client[Task] with Server

  val environmentTag: EnvironmentTag[Environment] = EnvironmentTag[Environment]

  override def bootstrap: TaskLayer[Environment] =
    ZLayer.make[Client[Task] with Server](
      ZLayer.scoped(
        EmberClientBuilder
          .default[Task]
          .withMaxTotal(1000)
          .withMaxPerKey(Function.const(1000))
          .build
          .toScopedZIO
      ),
      ZLayer.scoped(
        EmberServerBuilder
          .default[Task]
          .withMaxConnections(1000)
          .withHttpApp(
            HttpRoutes
              .of[Task] { case GET -> Root / "ping" => Ok("pong") }
              .orNotFound
          )
          .build
          .toScopedZIO
      )
    )

  override def run: RIO[Environment, ExitCode] =
    (
      for
        n <- ZIO.succeed(1000)
        pending <- Ref.make(n)
        _ <- ZIO.foreachPar(Range.inclusive(1, n))(i =>
          for
            client <- ZIO.service[Client[Task]]
            server <- ZIO.service[Server]
            text <- client.get(server.baseUri / "ping")(_.as[String])
            _ = assert(text == "pong")
            pending <- pending.updateAndGet(_ - 1)
            width = n.toString().length()
            fiberId <- ZIO.fiberId
            _ <- Console.printLine(
              s"Ping %${width}s complete, %${width}s remaining, fiber %s"
                .format(i, pending, fiberId)
            )
          yield ()
        )
      yield ()
    ).exitCode
