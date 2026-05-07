//> using scala 2.12.20
//> using dep org.scala-sbt::scripted-sbt-redux:1.10.7

package org.polyvariant.scsbt.build

import java.io.File
import sbt.scriptedtest.ScriptedRunner

// ScriptedRunner driver. Everything else (publish-local, fetching sbt-launch,
// writing repo.config) is the caller's responsibility — see justfile.
// Args: sbtTestRoot sbtLaunchJar repoConfig ivyHome sbtBoot pluginVersion [tests*]
object Scripted {

  def main(args: Array[String]): Unit = {
    require(args.length >= 6, "expected 6+ args: sbtTestRoot sbtLaunchJar repoConfig ivyHome sbtBoot pluginVersion [tests*]")

    val sbtTestRoot   = new File(args(0))
    val sbtLaunchJar  = new File(args(1))
    val repoConfig    = new File(args(2))
    val ivyHome       = new File(args(3))
    val sbtBoot       = new File(args(4))
    val pluginVersion = args(5)
    val tests         = args.drop(6)

    val launchOpts: Array[String] = Array(
      "-Xmx1g",
      s"-Dplugin.version=$pluginVersion",
      s"-Dsbt.ivy.home=${ivyHome.getAbsolutePath}",
      s"-Dsbt.boot.directory=${sbtBoot.getAbsolutePath}",
      s"-Dsbt.repository.config=${repoConfig.getAbsolutePath}",
      "-Dsbt.override.build.repos=true",
    )

    val testArgs: Array[String] = if (tests.isEmpty) Array("*/*") else tests
    println(s"[scripted] running ${testArgs.mkString(", ")}")

    new ScriptedRunner().run(
      sbtTestRoot,
      false, // bufferLog
      testArgs,
      sbtLaunchJar,
      launchOpts,
    )
  }
}
