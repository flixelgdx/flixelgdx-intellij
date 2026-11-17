/*
 * MIT License
 *
 * Copyright (c) 2026 stringdotjar
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.flixelgdx.intellij.cli

import com.google.gson.JsonObject
import com.intellij.openapi.progress.util.StandardProgressIndicatorBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Path

class FlxgdxRunnerTest {

  @get:Rule
  val tmp = TemporaryFolder()

  private val runner = FlxgdxRunner()

  @Before
  fun onlyOnUnix() {
    assumeFalse(System.getProperty("os.name").lowercase().contains("win"))
  }

  /** Writes a fake flxgdx that runs [body] as a shell script. */
  private fun fake(body: String): Path {
    val f = File(tmp.newFolder(), "flxgdx")
    f.writeText("#!/bin/sh\n$body\n")
    f.setExecutable(true)
    return f.toPath()
  }

  private class Recorder : CliEventListener {
    val calls = ArrayList<String>()
    var resultData: JsonObject? = null

    override fun onProgress(step: Int, total: Int?, message: String) {
      calls += "progress:$step/$total:$message"
    }

    override fun onLog(message: String, data: JsonObject?) {
      calls += "log:$message"
    }

    override fun onWarning(message: String) {
      calls += "warning:$message"
    }

    override fun onResult(data: JsonObject?) {
      calls += "result"
      resultData = data
    }

    override fun onError(code: String?, message: String) {
      calls += "error:$code:$message"
    }
  }

  @Test
  fun successRunsEventsInOrderAndReturnsData() {
    val exe = fake(
      """
      echo '{"protocol":1,"type":"progress","message":"One","step":1,"total":2}'
      echo '{"protocol":1,"type":"log","message":"note"}'
      echo '{"protocol":1,"type":"warning","message":"careful"}'
      echo '{"protocol":1,"type":"result","message":"done","data":{"version":"1.0.0"}}'
      """.trimIndent(),
    )
    val rec = Recorder()
    val result = runner.run(listOf("version"), listener = rec, executable = exe)

    assertTrue(result.isSuccess)
    assertEquals(0, result.exitCode)
    assertEquals(listOf("progress:1/2:One", "log:note", "warning:careful", "result"), rec.calls)
    assertEquals("1.0.0", result.data.stringAt("version"))
    assertEquals(listOf("careful"), result.warnings)
    assertNull(result.error)
  }

  @Test
  fun alwaysAppendsJsonAndNoInputFlags() {
    val exe = fake("""printf '{"type":"result","message":"%s","data":{"args":"%s"}}\n' ok "$*" """)
    val result = runner.run(listOf("add", "library", "--repo", "a/b"), executable = exe)
    assertEquals("add library --repo a/b --json --no-input", result.data.stringAt("args"))
  }

  @Test
  fun errorEventAndExitCodeAreReported() {
    val exe = fake(
      """
      echo '{"protocol":1,"type":"error","message":"not a project","code":"project_not_found"}'
      exit 5
      """.trimIndent(),
    )
    val rec = Recorder()
    val result = runner.run(listOf("info"), listener = rec, executable = exe)

    assertFalse(result.isSuccess)
    assertEquals(5, result.exitCode)
    assertEquals(CliError("project_not_found", "not a project"), result.error)
    assertEquals(listOf("error:project_not_found:not a project"), rec.calls)
    assertEquals("not a project", result.describe())
  }

  @Test
  fun nonZeroExitWithoutErrorEventStillFails() {
    val exe = fake("exit 7")
    val result = runner.run(listOf("x"), executable = exe)
    assertFalse(result.isSuccess)
    assertEquals(7, result.exitCode)
    assertEquals("flxgdx exited with code 7.", result.describe())
  }

  @Test
  fun malformedLinesAreSkippedAndLastLineWithoutNewlineIsKept() {
    val exe = fake(
      """
      echo 'garbage'
      echo '{"type":"progress"'
      echo '{"type":"nonsense","message":"?"}'
      printf '{"type":"result","message":"x","data":{"k":"v"}}'
      """.trimIndent(),
    )
    val result = runner.run(listOf("x"), executable = exe)
    assertTrue(result.isSuccess)
    assertEquals("v", result.data.stringAt("k"))
    assertEquals(1, result.results.size)
  }

  @Test
  fun stderrIsCapturedButNeverParsed() {
    val exe = fake("""echo '{"type":"error","message":"fake"}' >&2""")
    val result = runner.run(listOf("x"), executable = exe)
    assertNull(result.error)
    assertTrue(result.stderr.contains("fake"))
  }

  @Test
  fun workingDirectoryIsUsed() {
    val dir = tmp.newFolder("work")
    val exe = fake("""printf '{"type":"result","message":"m","data":{"cwd":"%s"}}\n' "$(pwd -P)" """)
    val result = runner.run(listOf("x"), workDir = dir.toPath(), executable = exe)
    assertEquals(dir.toPath().toRealPath().toString(), result.data.stringAt("cwd"))
  }

  @Test
  fun cancelStopsTheProcess() {
    val exe = fake("sleep 30")
    val indicator = StandardProgressIndicatorBase()
    Thread {
      Thread.sleep(400)
      indicator.cancel()
    }.start()
    val start = System.nanoTime()
    val result = runner.run(listOf("x"), indicator = indicator, executable = exe)
    assertTrue(result.cancelled)
    assertFalse(result.isSuccess)
    assertTrue("took too long to stop", (System.nanoTime() - start) / 1_000_000 < 15_000)
  }

  @Test
  fun timeoutStopsTheProcess() {
    val exe = fake("sleep 30")
    val result = runner.run(listOf("x"), executable = exe, timeoutMs = 500)
    assertTrue(result.timedOut)
    assertFalse(result.isSuccess)
  }

  @Test
  fun missingExecutableIsAStartFailure() {
    val result = runner.run(listOf("x"), executable = File(tmp.root, "nope").toPath())
    assertFalse(result.isSuccess)
    assertNotNull(result.startFailure)
  }

  @Test
  fun versionResultIsInterpreted() {
    val exe = fake("""echo '{"type":"result","message":"v","data":{"version":"1.2.0"}}'""")
    val ok = FlxgdxCli.interpret(exe, runner.run(listOf("version"), executable = exe), CliVersion.MINIMUM)
    assertEquals(CliStatus.Ok(exe, CliVersion(1, 2, 0)), ok)

    val old = fake("""echo '{"type":"result","message":"v","data":{"version":"0.9.9"}}'""")
    val tooOld = FlxgdxCli.interpret(old, runner.run(listOf("version"), executable = old), CliVersion.MINIMUM)
    assertEquals(CliStatus.TooOld(old, CliVersion(0, 9, 9), CliVersion.MINIMUM), tooOld)

    val odd = fake("""echo '{"type":"result","message":"v","data":{"version":"wat"}}'""")
    val broken = FlxgdxCli.interpret(odd, runner.run(listOf("version"), executable = odd), CliVersion.MINIMUM)
    assertTrue(broken is CliStatus.Broken)

    val failing = fake("exit 1")
    val failed = FlxgdxCli.interpret(failing, runner.run(listOf("version"), executable = failing), CliVersion.MINIMUM)
    assertTrue(failed is CliStatus.Broken)
  }
}
