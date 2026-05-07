// When the plugin under test is a Maven Central snapshot, the spawned sbt
// needs the snapshots host registered. The runner sets `plugin.snapshots=true`
// for that case.
if (sys.props.get("plugin.snapshots").contains("true"))
  resolvers += "central-snapshots" at "https://central.sonatype.com/repository/maven-snapshots/"
else Seq.empty[Setting[_]]

addSbtPlugin("org.polyvariant" % "scala-cli-sbt-plugin-poc" % sys.props.getOrElse("plugin.version", sys.error("plugin.version system property not set")))
