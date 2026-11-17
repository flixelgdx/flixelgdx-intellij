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

/** The failure a command ended with. */
data class CliError(val code: String?, val message: String)

/**
 * The outcome of one `flxgdx` run.
 *
 * @property exitCode the process exit code, or -1 when the process never started or was cancelled.
 * @property results the `data` of every `result` event, in order (events without data are skipped).
 * @property warnings the message of every `warning` event, in order.
 * @property error the last `error` event, or `null`.
 * @property cancelled whether the run was stopped through its progress indicator.
 * @property timedOut whether the run was stopped because it took too long.
 * @property startFailure why the process could not start (for example, flxgdx was not found), or `null`.
 * @property stderr whatever the process wrote to stderr; only for debugging, never parse it.
 */
data class CliResult(
  val exitCode: Int,
  val results: List<JsonObject> = emptyList(),
  val warnings: List<String> = emptyList(),
  val error: CliError? = null,
  val cancelled: Boolean = false,
  val timedOut: Boolean = false,
  val startFailure: String? = null,
  val stderr: String = "",
) {
  /** The `data` of the last `result` event, or `null` when there was none. */
  val data: JsonObject? get() = results.lastOrNull()

  /** `true` when the process exited with 0, reported no error, and was not stopped early. */
  val isSuccess: Boolean get() = exitCode == 0 && error == null && !cancelled && !timedOut && startFailure == null

  /** A short sentence that says why the run did not succeed, or says that it did. */
  fun describe(): String = when {
    startFailure != null -> startFailure
    cancelled -> "The command was cancelled."
    timedOut -> "The command took too long and was stopped."
    error != null -> error.message
    exitCode != 0 -> "flxgdx exited with code $exitCode."
    else -> "OK"
  }
}
