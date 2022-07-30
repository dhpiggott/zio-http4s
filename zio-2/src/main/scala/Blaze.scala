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

  def patchExecutor(patch: (Executor => Task[Unit])): Task[Unit] = for
    executor <- ZIO.executor
    _ <- Console.printLine(s"Patching $executor")
    _ <- patch(executor).tapError(e => Console.printLineError(e.toString))
    _ <- Console.printLine(s"Patched $executor")
  yield ()

  def disableAutoBlocking(executor: Executor): Task[Unit] = for 
    _ <- Console.printLine(s"executor: ${executor.getClass}")
    blockingLocations <- ZIO.attempt(executor.getClass().getDeclaredField("zio$internal$ZScheduler$$blockingLocations"))
    _ <- Console.printLine(s"blockingLocations: $blockingLocations")
    _ <- ZIO.attempt(blockingLocations.set(executor, EmptySet))
  yield ()

  object EmptySet extends Set[Any]:
    def iterator: Iterator[Any] = Iterator.empty
    def excl(elem: Any): Set[Any] = this
    def incl(elem: Any): Set[Any] = this
    def contains(elem: Any): Boolean = false

  type Environment = Client[Task] with Server

  val environmentTag: EnvironmentTag[Environment] = EnvironmentTag[Environment]

  override def bootstrap: TaskLayer[Environment] =
    ZLayer.make[Client[Task] with Server](
      ZLayer.fromZIO(
        patchExecutor(disableAutoBlocking)
      ),
      ZLayer.scoped(
        BlazeClientBuilder[Task]
          .withMaxTotalConnections(1000)
          .withMaxConnectionsPerRequestKey(Function.const(1000))
          .resource
          .toScopedZIO
      ),
      ZLayer.scoped(
        BlazeServerBuilder[Task]
          .withMaxConnections(1000)
          .withHttpApp(
            HttpRoutes
              .of[Task] { case GET -> Root / "ping" => Ok("pong") }
              .orNotFound
          )
          .resource
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
