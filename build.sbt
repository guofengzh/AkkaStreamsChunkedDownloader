name := "S3Downloader"

version := "1.0"

scalaVersion := "2.11.8"

val akkaVersion = "2.4.8"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % akkaVersion
libraryDependencies += "com.amazonaws" % "aws-java-sdk" % "1.9.31"
libraryDependencies += "com.typesafe.akka" %% "akka-stream" % akkaVersion
libraryDependencies += "com.typesafe.akka" %% "akka-http-core" % akkaVersion
libraryDependencies += "com.typesafe.akka" %% "akka-http-experimental" % akkaVersion
libraryDependencies += "com.typesafe.akka" %% "akka-http-testkit" % akkaVersion