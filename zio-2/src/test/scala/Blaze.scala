import org.http4s.*
import org.http4s.blaze.client.*
import org.http4s.blaze.server.*
import org.http4s.client.*
import org.http4s.client.dsl.*
import org.http4s.dsl.*
import org.http4s.server.*
import zio.*
import zio.interop.catz.*
import zio.test.*

object Blaze
    extends ZIOSpecDefault
    with Http4sDsl[Task]
    with Http4sClientDsl[Task]:

  override def spec: Spec[TestEnvironment, Throwable] =
    Range
      .inclusive(1, 100)
      .map(i =>
        test(s"Ping $i")(
          for
            client <- ZIO.service[Client[Task]]
            server <- ZIO.service[Server]
            text <- client.get(server.baseUri / "ping")(_.as[String])
          yield assert(text)(Assertion.equalTo("pong"))
        )
      )
      .reduce(_ + _)
      .provideCustomShared(
        ZLayer.scoped(
          BlazeClientBuilder[Task].resource.toScopedZIO
        ),
        ZLayer.scoped(
          BlazeServerBuilder[Task]
            .withHttpApp(
              HttpRoutes
                .of[Task] { case GET -> Root / "ping" => Ok("pong") }
                .orNotFound
            )
            .resource
            .toScopedZIO
        )
      )

  override def aspects: Chunk[TestAspectAtLeastR[TestEnvironment]] =
    Chunk(
      TestAspect.diagnose(30.seconds),
      TestAspect.parallel,
      TestAspect.timed,
      TestAspect.timeout(3.minutes),
      TestAspect.withLiveClock
    )
