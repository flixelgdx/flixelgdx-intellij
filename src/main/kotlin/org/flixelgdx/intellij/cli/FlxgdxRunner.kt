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
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.Key
import java.nio.charset.StandardCharsets
import java.nio.file.Path

/**
 * Runs `flxgdx` commands and turns their `--json` output into events and a typed [CliResult].
 *
 * This is the only place in the plugin that starts the `flxgdx` process. Think of it as a translator standing between
 * the IDE and the command line: you give it plain arguments, and it hands back structured events. Every call adds
 * `--json --no-input` for you, so `flxgdx` never prompts and never prints text that has to be parsed.
 *
 * Calls block until the process ends, so run them off the EDT (for example from a `Task.Backgroundable`, or through
 * [FlxgdxTasks.run]).
 *
 * Example:
 * ```
 * val result = service<FlxgdxRunner>().run(listOf("info"), workDir = projectDir)
 * if (result.isSuccess) println(result.data)
 * ```
 */
@Service(Service.Level.APP)
class FlxgdxRunner {

  /**
   * Runs `flxgdx <args> --json --no-input` and waits for it to finish.
   *
   * @param args the command and its flags, without `--json` and `--no-input`.
   * @param workDir the working directory, or `null` for the IDE's own.
   * @param indicator when it is canceled, the process is stopped and the result has `cancelled` set.
   * @param listener receives each event as it arrives, on a background thread.
   * @param executable the `flxgdx` to run; `null` uses the one found by [FlxgdxLocator].
   * @param timeoutMs stop the process after this many milliseconds; 0 means no limit.
   */
  fun run(
    args: List<String>,
    workDir: Path? = null,
    indicator: ProgressIndicator? = null,
    listener: CliEventListener? = null,
    executable: Path? = null,
    timeoutMs: Long = 0,
  ): CliResult {
    ApplicationManager.getApplication()?.assertIsNonDispatchThread()

    val exe = executable ?: service<FlxgdxLocator>().find()?.path
      ?: return CliResult(-1, startFailure = "flxgdx was not found.")

    val cmd = GeneralCommandLine(exe.toString())
      .withParameters(args + FIXED_FLAGS)
      .withCharset(StandardCharsets.UTF_8)
    if (workDir != null) cmd.withWorkDirectory(workDir.toFile())

    val collector = Collector(listener)
    val handler = try {
      OSProcessHandler(cmd)
    } catch (e: ExecutionException) {
      return CliResult(-1, startFailure = "flxgdx could not be started: ${e.message}")
    }
    handler.addProcessListener(collector)
    handler.startNotify()
    runCatching { handler.processInput?.close() }

    val deadline = if (timeoutMs > 0) System.nanoTime() + timeoutMs * 1_000_000 else Long.MAX_VALUE
    var cancelled = false
    var timedOut = false
    while (!handler.waitFor(POLL_MS)) {
      if (indicator?.isCanceled == true) cancelled = true
      if (System.nanoTime() > deadline) timedOut = true
      if (cancelled || timedOut) {
        handler.destroyProcess()
        if (!handler.waitFor(GRACE_MS)) {
          handler.process.destroyForcibly()
          handler.waitFor(GRACE_MS)
        }
        break
      }
    }
    collector.flush()

    val exitCode = if (cancelled || timedOut) -1 else runCatching { handler.process.exitValue() }.getOrDefault(-1)
    return CliResult(
      exitCode = exitCode,
      results = collector.results,
      warnings = collector.warnings,
      error = collector.error,
      cancelled = cancelled,
      timedOut = timedOut,
      stderr = collector.stderr.toString(),
    )
  }

  /** Collects process output, cuts it into lines, and forwards each decoded event. */
  private class Collector(private val listener: CliEventListener?) : ProcessAdapter() {
    val results = ArrayList<JsonObject>()
    val warnings = ArrayList<String>()
    val stderr = StringBuilder()
    var error: CliError? = null

    private val pending = StringBuilder()

    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
      when (outputType) {
        ProcessOutputTypes.STDOUT -> {
          pending.append(event.text)
          var nl = pending.indexOf("\n")
          while (nl >= 0) {
            handle(pending.substring(0, nl))
            pending.delete(0, nl + 1)
            nl = pending.indexOf("\n")
          }
        }
        ProcessOutputTypes.STDERR -> stderr.append(event.text)
      }
    }

    /** Handles a last line that had no newline at the end. */
    fun flush() {
      if (pending.isNotEmpty()) {
        handle(pending.toString())
        pending.setLength(0)
      }
    }

    private fun handle(line: String) {
      when (val e = CliEventParser.parse(line)) {
        null -> {}
        is CliEvent.Progress -> listener?.onProgress(e.step, e.total, e.message)
        is CliEvent.Log -> listener?.onLog(e.message, e.data)
        is CliEvent.Warning -> {
          warnings += e.message
          listener?.onWarning(e.message)
        }
        is CliEvent.Result -> {
          e.data?.let { results += it }
          listener?.onResult(e.data)
        }
        is CliEvent.Error -> {
          error = CliError(e.code, e.message)
          listener?.onError(e.code, e.message)
        }
      }
    }
  }

  companion object {
    private const val POLL_MS = 100L
    private const val GRACE_MS = 3000L
    private val FIXED_FLAGS = listOf("--json", "--no-input")
  }
}
