organization := "com.roboxue"

name := "niffler"

version := "0.1"

scalaVersion := "2.12.7"

lazy val `core-scala` = project.in(file("core-scala")).settings(
  libraryDependencies ++= Seq(
    "org.scala-lang" % "scala-reflect" % scalaVersion.value
  )
)
lazy val gen = project.in(file("gen")).settings(
  libraryDependencies ++= Seq(
    "org.scalactic" %% "scalactic" % "3.0.5",
    "org.scalatest" %% "scalatest" % "3.0.5" % "test"
  )
)

lazy val root = project.in(file(".")).aggregate(`core-scala`, gen)
