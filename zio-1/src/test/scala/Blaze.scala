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
import zio.duration.*
import zio.interop.catz.*
import zio.test.*

object Blaze
    extends DefaultRunnableSpec
    with Http4sDsl[Task]
    with Http4sClientDsl[Task]:

  override def spec: ZSpec[Environment, Any] =
    Range
      .inclusive(1, 100)
      .map(i =>
        testM(s"Ping $i")(
          for
            client <- ZIO.service[Client[Task]]
            server <- ZIO.service[Server]
            text <- client.get(server.baseUri / "ping")(_.as[String])
          yield assert(text)(Assertion.equalTo("pong"))
        )
      )
      .reduce(_ + _)
      .provideCustomLayerShared(
        (Blocking.live ++ Clock.live) >>> (RIO
          .runtime[Blocking with Clock]
          .toManaged_
          .flatMap(implicit runtime =>
            BlazeClientBuilder[Task].resource.toManagedZIO
          )
          .toLayer ++ RIO
          .runtime[Blocking with Clock]
          .toManaged_
          .flatMap(implicit runtime =>
            BlazeServerBuilder[Task]
              .withHttpApp(
                HttpRoutes
                  .of[Task] { case GET -> Root / "ping" => Ok("pong") }
                  .orNotFound
              )
              .resource
              .toManagedZIO
          )
          .toLayer).orDie
      )

  override def aspects: List[TestAspect[Nothing, Environment, Nothing, Any]] =
    List(
      TestAspect.diagnose(30.seconds),
      TestAspect.parallel,
      TestAspect.timed,
      TestAspect.timeout(3.minutes)
    )
