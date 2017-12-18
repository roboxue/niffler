import LibraryVersions._

import scala.language.postfixOps

// Global settings
organization in ThisBuild := "com.roboxue"
scalaVersion in ThisBuild := "2.11.8"
crossScalaVersions in ThisBuild := Seq("2.10.6", "2.11.8", "2.12.2")
name := "niffler"
description := "Proof of concept for using sbt syntax in production scala code"
noPublishSettings

lazy val core = nifflerProject("core", enablePublish = true)
  .settings(
    libraryDependencies ++= Seq(
      "io.monix" %% "monix" % monix,
      "com.lihaoyi" %% "sourcecode" % sourcecode,
      "com.chuusai" %% "shapeless" % shapeless,
      "com.typesafe.akka" %% "akka-actor" % akka,
      "com.typesafe.akka" %% "akka-testkit" % akka % Test,
      "org.scala-lang" % "scala-reflect" % scalaVersion.value,
      "org.jgrapht" % "jgrapht-core" % jgrapht
    )
  )

lazy val example = nifflerProject("example", enablePublish = false).dependsOn(core)

resolvers ++= Seq(Resolver.sonatypeRepo("releases"), Resolver.sonatypeRepo("snapshots"))

def nifflerProject(projectName: String, enablePublish: Boolean): Project =
  Project(projectName, file(projectName))
    .settings(commonSettings)
    .settings(projectMetadata)
    .settings(if (enablePublish) publishSettings else noPublishSettings)
    .settings(moduleName := s"niffler-$projectName")

lazy val commonSettings = Seq(
  scalacOptions := Seq("-deprecation", "-feature", "-language:implicitConversions", "-language:higherKinds", "-Xlint"),
  javacOptions := Seq("-source", "1.8", "-target", "1.8"),
  libraryDependencies ++= Seq(),
  libraryDependencies ++= Seq("org.scalatest" %% "scalatest" % scalatest).map(_ % "test")
)

lazy val projectMetadata =
  Seq(homepage := Some(url("https://github.com/roboxue/niffler")), startYear := Some(2017), scmInfo := {
    val base = "github.com/roboxue/niffler"
    Some(ScmInfo(url(s"https://$base"), s"scm:git:https://$base", Some(s"scm:git:git@$base")))
  })

// Publish and release settings
lazy val noPublishSettings = Seq(publish := (), publishLocal := (), publishArtifact := false)
lazy val publishSettings = Seq(credentials += Credentials(Path.userHome / ".ivy2" / ".credentials"))
releaseCrossBuild := true
