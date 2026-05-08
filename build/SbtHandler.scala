// Vendored from sbt source (Apache-2.0):
//   sbt/scripted/sbt/src/main/scala/sbt/test/SbtHandler.scala
//   sbt/main-command/src/main/scala/xsbt/IPC.scala
// Adapted to use util-scripted directly (no scripted-sbt-redux).

package org.polyvariant.scsbt.build

import java.io.{ BufferedReader, BufferedWriter, File, IOException, InputStreamReader, OutputStreamWriter }
import java.net.{ InetAddress, ServerSocket, Socket }

import scala.sys.process.{ BasicIO, Process }
import scala.util.control.NonFatal

import sbt.internal.scripted.{ StatementHandler, TestFailed }
import sbt.util.Logger

object IPC {
  private val portMin = 1025
  private val portMax = 65536
  private val loopback = InetAddress.getByName(null)

  def unmanagedServer: Server = new Server(makeServer)

  def makeServer: ServerSocket = {
    val random = new java.util.Random
    def nextPort = random.nextInt(portMax - portMin + 1) + portMin
    def createServer(attempts: Int): ServerSocket =
      if (attempts > 0)
        try new ServerSocket(nextPort, 1, loopback)
        catch { case NonFatal(_) => createServer(attempts - 1) }
      else sys.error("Could not connect to socket: maximum attempts exceeded")
    createServer(10)
  }

  final class Server private[IPC] (s: ServerSocket) {
    def port: Int = s.getLocalPort
    def close(): Unit = s.close()
    def isClosed: Boolean = s.isClosed
    def connection[T](f: IPC => T): T = {
      val client = s.accept()
      try f(new IPC(client))
      finally client.close()
    }
  }
}

final class IPC private[build] (s: Socket) {
  private val in = new BufferedReader(new InputStreamReader(s.getInputStream))
  private val out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream))

  def send(msg: String): Unit = { out.write(msg); out.newLine(); out.flush() }
  def receive: String = in.readLine()
}

final case class SbtInstance(process: Process, server: IPC.Server)

final class SbtHandler(
  directory: File,
  launcher: File,
  log: Logger,
  launchOpts: Seq[String] = Seq(),
) extends StatementHandler {

  type State = Option[SbtInstance]
  def initialState: State = None

  def apply(command: String, arguments: List[String], i: Option[SbtInstance]): Option[SbtInstance] =
    onSbtInstance(i) { (_, server) =>
      send((command :: arguments.map(escape)).mkString(" "), server)
      receive(command + " failed", server)
    }

  private def onSbtInstance(
    i: Option[SbtInstance],
  )(f: (Process, IPC.Server) => Unit
  ): Option[SbtInstance] = i match {
    case Some(SbtInstance(_, server)) if server.isClosed =>
      finish(i)
      onNewSbtInstance(f)
    case Some(SbtInstance(process, server)) =>
      f(process, server)
      i
    case None =>
      onNewSbtInstance(f)
  }

  private def onNewSbtInstance(f: (Process, IPC.Server) => Unit): Option[SbtInstance] = {
    val server = IPC.unmanagedServer
    val p =
      try newRemote(server)
      catch { case e: Throwable => server.close(); throw e }
    val ai = Some(SbtInstance(p, server))
    try { f(p, server); ai }
    catch { case e: Throwable => finish(ai); throw e }
  }

  def finish(state: Option[SbtInstance]): Unit = state match {
    case Some(SbtInstance(process, server)) =>
      try { send("exit", server); process.exitValue(); () }
      catch { case _: IOException => process.destroy() }
    case None => ()
  }

  private def send(message: String, server: IPC.Server): Unit =
    server.connection(_.send(message))

  private def receive(errorMessage: String, server: IPC.Server): Unit =
    server.connection { ipc =>
      val resultMessage = ipc.receive
      if (!resultMessage.toBoolean) throw new TestFailed(errorMessage)
    }

  private def newRemote(server: IPC.Server): Process = {
    val launcherJar = launcher.getAbsolutePath
    val globalBase = "-Dsbt.global.base=" + new File(directory, "global").getAbsolutePath
    val args =
      "java" :: (launchOpts.toList ++ List(globalBase, "-jar", launcherJar, "<" + server.port))
    val io = BasicIO(false, log).withInput(_.close())
    val p = Process(args, directory).run(io)
    val thread = new Thread() {
      override def run(): Unit = { p.exitValue(); server.close() }
    }
    thread.start()
    try receive("Remote sbt initialization failed", server)
    catch {
      case _: java.net.SocketException => throw new TestFailed("Remote sbt initialization failed")
    }
    p
  }

  import java.util.regex.Pattern.{ quote => q }
  private def escape(argument: String): String =
    if (argument.contains(" "))
      "\"" + argument.replaceAll(q("""\"""), """\\""").replaceAll(q("\""), "\\\"") + "\""
    else argument
}
