resolvers += Resolver.typesafeRepo("releases")
logLevel := Level.Warn
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.7.0")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.6")
addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.7")
