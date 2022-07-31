name := "zio-http4s"

ThisBuild / scalaVersion := "3.1.3"
ThisBuild / scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.5.0"
ThisBuild / semanticdbEnabled := true

addCommandAlias("fmt", "all scalafmtSbt scalafmtAll")
addCommandAlias("fix", "scalafixAll")

lazy val `zio-1` = project
  .settings(
    fork := true,
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % "1.2.11",
      "dev.zio" %% "izumi-reflect" % "2.1.3",
      "dev.zio" %% "zio" % "1.0.16",
      "dev.zio" %% "zio-interop-cats" % "13.0.0.0",
      "org.http4s" %% "http4s-blaze-client" % "0.23.12",
      "org.http4s" %% "http4s-blaze-server" % "0.23.12",
      "org.http4s" %% "http4s-client" % "0.23.14",
      "org.http4s" %% "http4s-core" % "0.23.14",
      "org.http4s" %% "http4s-dsl" % "0.23.14",
      "org.http4s" %% "http4s-server" % "0.23.14",
      "org.typelevel" %% "cats-core" % "2.8.0",
      "org.typelevel" %% "cats-effect-kernel" % "3.3.14"
    )
  )

lazy val `zio-2` = project
  .settings(
    fork := true,
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % "1.2.11",
      "dev.zio" %% "izumi-reflect" % "2.1.3",
      "dev.zio" %% "zio" % "2.0.0",
      "dev.zio" %% "zio-interop-cats" % "3.3.0",
      "dev.zio" %% "zio-stacktracer" % "2.0.0",
      "org.http4s" %% "http4s-blaze-client" % "0.23.12",
      "org.http4s" %% "http4s-blaze-server" % "0.23.12",
      "org.http4s" %% "http4s-client" % "0.23.14",
      "org.http4s" %% "http4s-core" % "0.23.14",
      "org.http4s" %% "http4s-dsl" % "0.23.14",
      "org.http4s" %% "http4s-server" % "0.23.14",
      "org.typelevel" %% "cats-core" % "2.8.0",
      "org.typelevel" %% "cats-effect-kernel" % "3.3.14"
    )
  )
