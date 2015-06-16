name := "S3Downloader"

version := "1.0"

scalaVersion := "2.11.6"

val akkaStreamV = "1.0-RC3"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.3.11"
libraryDependencies += "com.amazonaws" % "aws-java-sdk" % "1.9.31"
libraryDependencies += "com.typesafe.akka" % "akka-stream-experimental_2.11" % akkaStreamV
libraryDependencies += "com.typesafe.akka" % "akka-http-core-experimental_2.11" % akkaStreamV
libraryDependencies += "com.typesafe.akka" % "akka-http-experimental_2.11" % akkaStreamV
libraryDependencies += "com.typesafe.akka" % "akka-http-testkit-experimental_2.11" % akkaStreamV