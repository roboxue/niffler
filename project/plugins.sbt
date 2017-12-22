resolvers += Resolver.typesafeRepo("releases")
logLevel := Level.Warn
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.5.0")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.3")
addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.3")
addSbtPlugin("com.lucidchart" % "sbt-scalafmt" % "1.14")

