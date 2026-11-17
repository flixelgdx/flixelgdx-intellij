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
package org.flixelgdx.intellij.debugger

import com.google.gson.JsonObject
import org.flixelgdx.intellij.cli.stringAt

/** Turns the events of `flxgdx install` into progress for the IDE. */
object InstallProgress {

  /**
   * Works out how far the whole install is, from a `log` event of the download.
   *
   * The install has [total] steps, and the download is one of them. While it runs, `flxgdx` sends `log` events with
   * `data` such as `{"stage":"download","bytes":12345,"total":54945240}`. The finished steps count fully and the
   * download counts by the share of bytes that arrived, so the bar moves smoothly instead of jumping.
   *
   * @param step the number of the latest `progress` step (the first step is 1).
   * @param total the number of steps, or `null` when it is not known.
   * @param data the `data` of the log event.
   * @return a fraction between 0 and 1, or `null` when the event is not a download update or has no usable sizes.
   */
  fun fraction(step: Int, total: Int?, data: JsonObject?): Double? {
    if (total == null || total <= 0 || step <= 0) return null
    if (data.stringAt("stage") != "download") return null
    val bytes = number(data, "bytes") ?: return null
    val size = number(data, "total") ?: return null
    if (size <= 0 || bytes < 0) return null
    val share = (bytes.toDouble() / size).coerceIn(0.0, 1.0)
    return ((step - 1) + share) / total
  }

  private fun number(data: JsonObject?, key: String): Long? =
    data?.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asLong
}

/**
 * The result of `flxgdx install debugger`.
 *
 * @property action `installed`, `upgraded`, `reinstalled` or `unchanged`.
 * @property version the release tag that is installed.
 * @property executable the path to start the program with.
 */
data class InstallResult(val action: String, val version: String, val executable: String) {

  /** A short sentence for a notification. */
  fun summary(): String {
    val what = if (version.isEmpty()) "The debugger" else "The debugger $version"
    return when (action) {
      "unchanged" -> "$what is already installed."
      "upgraded" -> "$what was upgraded."
      "reinstalled" -> "$what was reinstalled."
      else -> "$what was installed."
    }
  }

  companion object {
    /** Reads the `data` of the result event. Missing keys become empty text. */
    fun parse(data: JsonObject?): InstallResult = InstallResult(
      data.stringAt("action").orEmpty(),
      data.stringAt("version").orEmpty(),
      data.stringAt("executable").orEmpty(),
    )
  }
}
