name := "shopping-cart-service"

scalaVersion := "3.0.1"
inConfig(Compile)(scalaBinaryVersion := "2.13")

enablePlugins(AkkaGrpcPlugin)

lazy val util = (project in file("."))
  .settings(
    libraryDependencies ~= (_.filter { module =>
      module.organization != "com.thesamet.scalapb"
    })
  )

Compile / scalacOptions ++= Seq(
  "-target:11",
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Xlog-reflective-calls",
  "-Xlint"
)
Compile / javacOptions ++= Seq("-Xlint:unchecked", "-Xlint:deprecation")

Test / parallelExecution := false
Test / testOptions += Tests.Argument("-oDF")
Test / logBuffered := false

run / fork := false
Global / cancelable := false // ctrl-c

val AkkaVersion = "2.6.16"
val AkkaHttpVersion = "10.2.6"
val AkkaManagementVersion = "1.0.10"
val AkkaPersistenceJdbcVersion = "5.0.0"
val AlpakkaKafkaVersion = "2.0.7"
val AkkaProjectionVersion = "1.1.0"
val ScalikeJdbcVersion = "3.5.0"

/**
 * docker config
 */
// make version compatible with docker for publishing
ThisBuild / dynverSeparator := "-"


mainClass in (Compile, run) := Some("shopping.cart.Main")

enablePlugins(JavaServerAppPackaging, DockerPlugin)

dockerExposedPorts := Seq(9101, 8558, 2551)
dockerBaseImage := "adoptopenjdk:11-jre-hotspot"


/**
 * dependencies
 */
libraryDependencies ++= Seq(
  // 1. Basic dependencies for a clustered application
  "com.typesafe.akka" %% "akka-stream" % AkkaVersion cross CrossVersion.for3Use2_13,
  "com.typesafe.akka" %% "akka-cluster-typed" % AkkaVersion cross CrossVersion.for3Use2_13,
  "com.typesafe.akka" %% "akka-cluster-sharding-typed" % AkkaVersion cross CrossVersion.for3Use2_13,
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test cross CrossVersion.for3Use2_13,
  "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion % Test cross CrossVersion.for3Use2_13,

  // Akka Management powers Health Checks and Akka Cluster Bootstrapping
  "com.lightbend.akka.management" %% "akka-management" % AkkaManagementVersion cross CrossVersion.for3Use2_13,
  "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion cross CrossVersion.for3Use2_13,
  "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion cross CrossVersion.for3Use2_13,
  "com.lightbend.akka.management" %% "akka-management-cluster-http" % AkkaManagementVersion cross CrossVersion.for3Use2_13,
  "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % AkkaManagementVersion cross CrossVersion.for3Use2_13,
  "com.lightbend.akka.discovery" %% "akka-discovery-kubernetes-api" % AkkaManagementVersion cross CrossVersion.for3Use2_13,
  "com.typesafe.akka" %% "akka-discovery" % AkkaVersion cross CrossVersion.for3Use2_13,

  // Common dependencies for logging and testing
  "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion cross CrossVersion.for3Use2_13,
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "org.scalatest" %% "scalatest" % "3.2.9" % Test,

  // 2. Using gRPC and/or protobuf
  "com.typesafe.akka" %% "akka-http2-support" % AkkaHttpVersion cross CrossVersion.for3Use2_13,

  // 3. Using Akka Persistence
  "com.typesafe.akka" %% "akka-persistence-typed" % AkkaVersion cross CrossVersion.for3Use2_13,
  "com.typesafe.akka" %% "akka-serialization-jackson" % AkkaVersion cross CrossVersion.for3Use2_13,
  "com.lightbend.akka" %% "akka-persistence-jdbc" % AkkaPersistenceJdbcVersion cross CrossVersion.for3Use2_13,
  "com.typesafe.akka" %% "akka-persistence-testkit" % AkkaVersion % Test cross CrossVersion.for3Use2_13,
  "com.typesafe.akka" %% "akka-persistence-cassandra" % "1.0.5" cross CrossVersion.for3Use2_13,

  "com.thesamet.scalapb" %% "lenses" % "0.11.3" cross CrossVersion.for3Use2_13,
  "com.thesamet.scalapb" %% "scalapb-runtime" % "0.11.3" cross CrossVersion.for3Use2_13,

  "org.typelevel" %% "cats-core" % "2.6.1",
  "org.typelevel" %% "cats-free" % "2.6.1",

  "dev.zio" %% "zio" % "2.0.0-M2",
)
