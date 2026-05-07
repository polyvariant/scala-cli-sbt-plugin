//> using scala 2.12.20
//> using dep org.scala-sbt::scripted-sbt-redux:1.10.7
//> using dep io.get-coursier:coursier_2.12:2.1.24
//> using dep com.lihaoyi::os-lib:0.11.8

package org.polyvariant.scsbt.build

import java.io.File
import sbt.scriptedtest.ScriptedRunner

object Runner {

  val PluginOrg    = "org.polyvariant"
  val PluginName   = "scala-cli-sbt-plugin-poc"
  // sbt 1.x cross-paths convention for plugins published as Maven jars:
  // <name>_<scalaBinary>_<sbtBinary>
  val PluginModule = s"${PluginName}_2.12_1.0"

  def fetchLauncher(sbtVersion: String): File = {
    val files = coursier.Fetch()
      .addDependencies(coursier.Dependency(
        coursier.Module(
          coursier.Organization("org.scala-sbt"),
          coursier.ModuleName("sbt-launch"),
        ),
        sbtVersion,
      ))
      .run()
    files.find(_.getName.startsWith("sbt-launch"))
      .getOrElse(sys.error(s"could not locate sbt-launch in fetch result: $files"))
  }

  def scripted(
    pluginDir: os.Path,
    sbtTestRoot: os.Path,
    pluginVersion: String,
    sbtVersion: String,
    workDir: os.Path,
    tests: Seq[String],
    publishLocal: Boolean,
  ): Unit = {
    os.remove.all(workDir)
    os.makeDir.all(workDir)
    val ivyHome = workDir / "ivy"
    val sbtBoot = workDir / "sbt-boot"
    val m2Home  = workDir / "m2"
    os.makeDir.all(ivyHome)
    os.makeDir.all(sbtBoot)
    os.makeDir.all(m2Home)

    if (publishLocal) {
      println(s"[runner] publishing $PluginOrg:$PluginModule:$pluginVersion to $m2Home")
      publishLocalForScripted(pluginDir, m2Home, pluginVersion)
    } else {
      println(
        s"[runner] skipping local publish; expecting $PluginOrg:$PluginModule:$pluginVersion to be available remotely",
      )
    }

    val launcher = fetchLauncher(sbtVersion)
    println(s"[runner] using sbt-launch: $launcher")

    val repoConfig = writeRepoConfig(workDir, m2Home, includeScratch = publishLocal)
    val launchOpts: Array[String] = Array(
      "-Xmx1g",
      s"-Dplugin.version=$pluginVersion",
      s"-Dsbt.ivy.home=$ivyHome",
      s"-Dsbt.boot.directory=$sbtBoot",
      s"-Dsbt.repository.config=$repoConfig",
      "-Dsbt.override.build.repos=true",
    )

    val testArgs: Array[String] = if (tests.isEmpty) Array("*/*") else tests.toArray
    println(s"[runner] running scripted tests: ${testArgs.mkString(", ")}")

    val runner = new ScriptedRunner
    runner.run(
      sbtTestRoot.toIO, // resourceBaseDirectory
      false,            // bufferLog
      testArgs,         // tests
      launcher,         // launcherJar
      launchOpts,       // launchOpts
    )
  }

  /** Publish to Maven Central (or wherever publish.repository points). */
  def publish(pluginDir: os.Path, extraArgs: Seq[String]): Unit = {
    val cmd = Seq(
      "scala-cli", "--power", "publish",
      pluginDir.toString,
      "--module-name", PluginModule,
    ) ++ extraArgs
    println(s"[runner] ${cmd.mkString(" ")}")
    val result = os.proc(cmd).call(check = false, stdout = os.Inherit, stderr = os.Inherit)
    if (result.exitCode != 0) sys.error(s"scala-cli publish failed: ${cmd.mkString(" ")}")
  }

  private def publishLocalForScripted(
    pluginDir: os.Path,
    m2Home: os.Path,
    version: String,
  ): Unit = {
    val cmd = Seq(
      "scala-cli", "--power", "publish", "local",
      pluginDir.toString,
      "--maven-local",
      "--m2-home", m2Home.toString,
      "--module-name", PluginModule,
      "--organization", PluginOrg,
      "--name", PluginName,
      "--project-version", version,
      "--signer", "none",
    )
    val result = os.proc(cmd).call(check = false, stdout = os.Inherit, stderr = os.Inherit)
    if (result.exitCode != 0) sys.error(s"scala-cli publish local failed: ${cmd.mkString(" ")}")
  }

  private def writeRepoConfig(
    workDir: os.Path,
    m2Home: os.Path,
    includeScratch: Boolean,
  ): os.Path = {
    val cfg = workDir / "repo.config"
    val scratch =
      if (includeScratch) s"  scratch-m2: file://${m2Home.toString}\n"
      else ""
    val content =
      s"""[repositories]
         |  local
         |${scratch}  maven-central
         |  sbt-plugin-releases: https://repo.scala-sbt.org/scalasbt/sbt-plugin-releases/, [organization]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[revision]/[type]s/[artifact](-[classifier]).[ext]
         |""".stripMargin
    os.write.over(cfg, content)
    cfg
  }

  def main(args: Array[String]): Unit = {
    val root = sys.env.get("PROJECT_ROOT").map(os.Path(_)).getOrElse(os.pwd)
    val pluginDir     = root / "plugin"
    val sbtTestRoot   = pluginDir / "src" / "sbt-test"
    val workDir       = root / ".scripted-work"
    val pluginVersion = sys.env.getOrElse("PLUGIN_VERSION", "0.0.0-SNAPSHOT")
    val sbtVersion    = sys.env.getOrElse("SBT_VERSION", "1.10.7")

    def runScripted(rest: List[String]): Unit = {
      val (skipPublish, tests) = rest.partition(_ == "--no-publish")
      scripted(
        pluginDir,
        sbtTestRoot,
        pluginVersion,
        sbtVersion,
        workDir,
        tests,
        publishLocal = skipPublish.isEmpty,
      )
    }

    args.toList match {
      case "scripted" :: rest => runScripted(rest)
      case "publish" :: rest  => publish(pluginDir, rest)
      case Nil                => runScripted(Nil)
      case other =>
        sys.error(
          s"unknown command: ${other.mkString(" ")}. usage: scripted [--no-publish] [tests*] | publish [extra-args*]",
        )
    }
  }
}
