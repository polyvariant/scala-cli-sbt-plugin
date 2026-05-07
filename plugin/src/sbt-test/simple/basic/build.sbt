name := "basic-test"

scalaVersion := "2.13.14"

TaskKey[Unit]("checkGreeting") := {
  val expected = "hello from scala-cli sbt plugin"
  val actual = helloPluginGreeting.value
  assert(actual == expected, s"expected '$expected' but got '$actual'")
}

TaskKey[Unit]("checkWritten") := {
  val f = helloPluginWrite.value
  val actual = IO.read(f)
  assert(actual.startsWith("hello"), s"unexpected content in $f: $actual")
}
