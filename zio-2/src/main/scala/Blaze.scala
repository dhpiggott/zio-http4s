import org.http4s.*
import org.http4s.blaze.client.*
import org.http4s.blaze.server.*
import org.http4s.client.*
import org.http4s.client.dsl.*
import org.http4s.dsl.*
import org.http4s.server.*
import zio.*
import zio.interop.catz.*

object Blaze extends ZIOApp with Http4sClientDsl[Task] with Http4sDsl[Task]:

  val totalPings = 10000
  val pingsAtATime = 1000

  type Environment = Client[Task] with Server

  val environmentTag: EnvironmentTag[Environment] = EnvironmentTag[Environment]

  override def bootstrap: TaskLayer[Environment] =
    ZLayer.make[Client[Task] with Server](
      ZLayer.scoped(
        for
          executor <- ZIO.executor
          client <- BlazeClientBuilder[Task]
            .withExecutionContext(executor.asExecutionContext)
            .withMaxTotalConnections(pingsAtATime)
            .withMaxConnectionsPerRequestKey(Function.const(pingsAtATime))
            .resource
            .toScopedZIO
        yield client
      ),
      ZLayer.scoped(
        for
          executor <- ZIO.executor
          server <- BlazeServerBuilder[Task]
            .withExecutionContext(executor.asExecutionContext)
            .withMaxConnections(1000)
            .withHttpApp(
              HttpRoutes
                .of[Task] { case GET -> Root / "ping" => Ok("pong") }
                .orNotFound
            )
            .resource
            .toScopedZIO
        yield server
      )
    )

  override def run: URIO[Environment, ExitCode] =
    (
      for
        pending <- Ref.make(totalPings)
        _ <- ZIO.withParallelism(pingsAtATime)(
          ZIO.foreachPar(Range.inclusive(1, totalPings))(i =>
            for
              client <- ZIO.service[Client[Task]]
              server <- ZIO.service[Server]
              text <- client.get(server.baseUri / "ping")(_.as[String])
              _ = assert(text == "pong")
              pending <- pending.updateAndGet(_ - 1)
              width = totalPings.toString().length()
              fiberId <- ZIO.fiberId
              _ <- Console.printLine(
                s"Ping %${width}s complete, %${width}s remaining, fiber %s"
                  .format(i, pending, fiberId)
              )
            yield ()
          )
        )
      yield ()
    ).exitCode
