organization := "com.roboxue"

name := "niffler"

version := "0.1"

scalaVersion := "2.12.7"

lazy val coreScala = project.in(file("core-scala")).settings(
  libraryDependencies ++= Seq(
    "org.scala-lang" % "scala-reflect" % scalaVersion.value,
    "org.scalactic" %% "scalactic" % "3.0.5",
    "com.google.guava" % "guava" % "27.0-jre",
    "org.apache.commons" % "commons-lang3" % "3.8.1",
    "com.lihaoyi" %% "sourcecode" % "0.1.4",
    "org.json4s" %% "json4s-jackson" % "3.6.1",
    "org.scalatest" %% "scalatest" % "3.0.5" % "test"
  )
)
lazy val gen = project.in(file("gen")).settings(
  libraryDependencies ++= Seq(
    "org.scalactic" %% "scalactic" % "3.0.5",
    "org.scalatest" %% "scalatest" % "3.0.5" % "test"
  )
)
lazy val serverScala = project.in(file("server-scala")).settings(
  libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-http"   % "10.1.5",
    "com.typesafe.akka" %% "akka-stream" % "2.5.12",
    "com.google.guava" % "guava" % "27.0-jre",
    "org.json4s" %% "json4s-jackson" % "3.6.1",
    "org.scalactic" %% "scalactic" % "3.0.5",
    "org.scalatest" %% "scalatest" % "3.0.5" % "test"
  )
).dependsOn(coreScala)

lazy val root = project.in(file(".")).aggregate(coreScala, serverScala, gen)
