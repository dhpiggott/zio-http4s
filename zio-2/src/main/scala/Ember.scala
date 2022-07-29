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
        EmberClientBuilder.default[Task].build.toScopedZIO
      ),
      ZLayer.scoped(
        EmberServerBuilder
          .default[Task]
          .withHttpApp(
            HttpRoutes
              .of[Task] { case GET -> Root / "ping" => Ok("pong") }
              .orNotFound
          )
          .build
          .toScopedZIO
      )
    )

  override def run: RIO[Environment, ExitCode] = ZIO
    .foreachPar(
      Range.inclusive(1, 100)
    )(i =>
      for
        client <- ZIO.service[Client[Task]]
        server <- ZIO.service[Server]
        text <- client.get(server.baseUri / "ping")(_.as[String])
        _ = assert(text == "pong")
        _ <- Console.printLine(s"Ping $i")
      yield ()
    )
    .exitCode
