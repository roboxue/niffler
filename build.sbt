organization := "com.roboxue"

name := "niffler"

version := "0.1"

scalaVersion := "2.12.7"

lazy val `core-scala` = project.in(file("core-scala")).settings(
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

lazy val root = project.in(file(".")).aggregate(`core-scala`, gen)
