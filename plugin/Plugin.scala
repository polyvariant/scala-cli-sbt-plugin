//> using scala 2.12.20
//> using compileOnly.dep org.scala-sbt:sbt:1.10.7
//> using resourceDir resources

//> using publish.organization org.polyvariant
//> using publish.name scala-cli-sbt-plugin-poc
//> using publish.computeVersion git:tag
//> using publish.developers "kubukoz|Jakub Kozłowski|https://github.com/kubukoz"
//> using publish.license Apache-2.0
//> using publish.repository central
//> using publish.url https://github.com/polyvariant/scala-cli-sbt-plugin
//> using publish.vcs github:polyvariant/scala-cli-sbt-plugin
//> using publish.secretKey env:PGP_SECRET
//> using publish.secretKeyPassword env:PGP_PASSPHRASE

package org.polyvariant.scsbt

import sbt._
import sbt.Keys._

object HelloPlugin extends AutoPlugin {
  override def trigger = allRequirements

  object autoImport {
    val helloPluginGreeting = settingKey[String]("Greeting message")
    val helloPluginWrite    = taskKey[File]("Write the greeting to target/hello.txt")
  }

  import autoImport._

  override lazy val projectSettings: Seq[Setting[_]] = Seq(
    helloPluginGreeting := "hello from scala-cli sbt plugin",
    helloPluginWrite := {
      val out = target.value / "hello.txt"
      IO.write(out, helloPluginGreeting.value)
      streams.value.log.info(s"Wrote greeting to $out")
      out
    },
  )
}
