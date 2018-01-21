import LibraryVersions._
import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._

import scala.language.postfixOps

// Global settings
name := "niffler"
organization in ThisBuild := "com.roboxue"
description := "Data flow programming paradigm library for Scala"

scalaVersion in ThisBuild := "2.11.8"
crossScalaVersions in ThisBuild := Seq("2.11.8", "2.12.2")
scalaModuleInfo := scalaModuleInfo.value map {
  _.withOverrideScalaVersion(true)
}

lazy val root = project.in(file(".")).aggregate(core, example, monitoring)

lazy val core = nifflerProject("core", enablePublish = true)
  .settings(
    sourceGenerators in Compile += {
      CodeGenTokenSyntax.generateCode
    },
    CodeGenTokenSyntax.generateCode := {
      sLog.value.info("Start code generation")
      val f1 = CodeGenTokenSyntax.saveToFile(sourceManaged.value / "main")
      sLog.value.info(s"Generation complete ${f1.toURI}")
      Seq(f1)
    },
    libraryDependencies ++= Seq(
      "io.monix" %% "monix" % monix,
      "com.lihaoyi" %% "sourcecode" % sourcecode,
      "com.typesafe.akka" %% "akka-actor" % akka,
      "com.typesafe.akka" %% "akka-testkit" % akka % Test,
      "org.scala-lang" % "scala-reflect" % scalaVersion.value,
      "com.google.guava" % "guava" % guava,
      "org.jgrapht" % "jgrapht-core" % jgrapht
    ),
    parallelExecution in Test := false
  )

lazy val monitoring = nifflerProject("monitoring", enablePublish = true)
  .dependsOn(core)
  .enablePlugins(SbtTwirl)
  .settings(
    TwirlKeys.templateImports ++= Seq(
      "com.roboxue.niffler.monitoring._"),
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-generic" % circe,
      "io.circe" %% "circe-literal" % circe,
      "org.http4s" %% "http4s-dsl" % http4s,
      "org.http4s" %% "http4s-twirl" % http4s,
      "org.http4s" %% "http4s-circe" % http4s,
      "org.http4s" %% "http4s-blaze-server" % http4s
    )
  )

lazy val example = nifflerProject("example", enablePublish = false).dependsOn(core, monitoring)

resolvers ++= Seq(Resolver.sonatypeRepo("releases"), Resolver.sonatypeRepo("snapshots"))

def nifflerProject(projectName: String, enablePublish: Boolean): Project =
  Project(projectName, file(projectName))
    .settings(commonSettings)
    .settings(if (enablePublish) publishSettings else noPublishSettings)
    .settings(moduleName := s"niffler-$projectName")

lazy val commonSettings = Seq(
  scalacOptions := Seq("-deprecation", "-feature", "-language:implicitConversions", "-language:higherKinds"),
  javacOptions := Seq("-source", "1.8", "-target", "1.8"),
  libraryDependencies ++= Seq(),
  libraryDependencies ++= Seq("org.scalatest" %% "scalatest" % scalatest).map(_ % "test")
)

// Publish and release settings
lazy val noPublishSettings = Seq(skip in publish := true)
lazy val publishSettings = Seq(
  releasePublishArtifactsAction := PgpKeys.publishSigned.value,
  releaseCrossBuild := true,

  homepage := Some(url("https://github.com/roboxue/niffler")),
  licenses := Seq("Apache License, Version 2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0.txt")),
  startYear := Some(2017),
  developers := List(Developer("roboxue", "Robert Xue", "roboxue@roboxue.com", url("http://www.roboxue.com"))),

  pomIncludeRepository := { _ => false },
  publishMavenStyle := true,
  publishArtifact in Test := false,
  scmInfo := {
    val base = "github.com/roboxue/niffler"
    Some(ScmInfo(url(s"https://$base"), s"scm:git:https://$base", Some(s"scm:git:git@$base")))
  }
)

// Turn off publish for the root project
skip in publish := true

// Release settings for `sbt release`
publishTo in ThisBuild := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
}
releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  releaseStepCommand("publishSigned"),
  setNextVersion,
  commitNextVersion,
  releaseStepCommand("sonatypeReleaseAll"),
  pushChanges
)
