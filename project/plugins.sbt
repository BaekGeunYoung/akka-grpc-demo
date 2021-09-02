//resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

addSbtPlugin("com.lightbend.akka.grpc" % "sbt-akka-grpc" % "2.1.0")
//addSbtPlugin("com.lightbend.akka.grpc" % "sbt-akka-grpc" % "2.0.0")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.2")
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.8.1")
addSbtPlugin("com.dwijnand" % "sbt-dynver" % "4.1.1")
