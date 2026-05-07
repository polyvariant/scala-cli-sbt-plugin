addSbtPlugin("org.polyvariant" % "scala-cli-sbt-plugin-poc" % sys.props.getOrElse("plugin.version", sys.error("plugin.version system property not set")))
