val tapirVersion = "1.10.15"
val http4sVersion = "0.23.27"
val blazeVersion = "0.23.16"
val sttpVersion = "3.9.7"
val logbackVersion = "1.5.6"
val scalaLoggingVersion = "3.9.5"

lazy val rootProject = (project in file(".")).settings(
  Seq(
    name := "tapir-correlation-id",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := "2.13.14",
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-dsl" % http4sVersion,
      "org.http4s" %% "http4s-blaze-server" % blazeVersion,
      "org.http4s" %% "http4s-blaze-client" % blazeVersion,
      "org.http4s" %% "http4s-circe" % http4sVersion,
      "com.softwaremill.sttp.client3" %% "async-http-client-backend-cats" % sttpVersion,
      "com.softwaremill.sttp.tapir" %% "tapir-http4s-server" % tapirVersion,
      "com.softwaremill.sttp.tapir" %% "tapir-sttp-client" % tapirVersion,
      "ch.qos.logback" % "logback-classic" % logbackVersion,
      "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingVersion,
      "com.softwaremill.sttp.client3" %% "async-http-client-backend-fs2" % sttpVersion,
    )
  )
)
