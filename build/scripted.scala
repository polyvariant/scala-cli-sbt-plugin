//> using scala 2.12.20
//> using dep org.scala-sbt::util-scripted:1.12.11

// ScriptedRunner driver. Everything else (publish-locally, fetching sbt-launch)
// is the caller's responsibility — see justfile.
// Args: sbtTestRoot sbtLaunchJar ivyHome sbtBoot pluginVersion [propKey=propVal*] -- [tests*]
package org.polyvariant.scsbt.build

import java.io.File
import sbt.internal.scripted.{
  CommentHandler,
  FileCommands,
  HandlersProvider,
  ScriptConfig,
  ScriptedRunnerImpl,
  StatementHandler,
}
import sbt.util.{ Level, Logger }

object Scripted {

  private val ConsoleLogger: Logger = new Logger {
    def trace(t: => Throwable): Unit  = t.printStackTrace()
    def success(message: => String): Unit = println(s"[success] $message")
    def log(level: Level.Value, message: => String): Unit = println(s"[$level] $message")
  }

  def main(args: Array[String]): Unit = {
    require(
      args.length >= 5,
      "expected 5+ args: sbtTestRoot sbtLaunchJar ivyHome sbtBoot pluginVersion [props*] -- [tests*]",
    )

    val sbtTestRoot   = new File(args(0))
    val sbtLaunchJar  = new File(args(1))
    val ivyHome       = new File(args(2))
    val sbtBoot       = new File(args(3))
    val pluginVersion = args(4)

    val rest = args.drop(5)
    val (extraProps, tests) = rest.indexOf("--") match {
      case -1  => (rest, Array.empty[String])
      case sep => (rest.take(sep), rest.drop(sep + 1))
    }

    val launchOpts: Seq[String] = Seq(
      "-Xmx1g",
      s"-Dplugin.version=$pluginVersion",
      s"-Dsbt.ivy.home=${ivyHome.getAbsolutePath}",
      s"-Dsbt.boot.directory=${sbtBoot.getAbsolutePath}",
    ) ++ extraProps.map("-D" + _)

    val handlersProvider: HandlersProvider = (config: ScriptConfig) =>
      Map(
        '$' -> new FileCommands(config.testDirectory()),
        '#' -> CommentHandler,
        '>' -> new SbtHandler(
          config.testDirectory(),
          sbtLaunchJar,
          config.logger(),
          launchOpts,
        ),
      )

    val testArgs: Array[String] = if (tests.isEmpty) Array("*/*") else tests
    println(s"[scripted] running ${testArgs.mkString(", ")}")

    ScriptedRunnerImpl.run(sbtTestRoot, false /* bufferLog */, testArgs, handlersProvider)
  }
}
