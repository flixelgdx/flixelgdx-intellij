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

/** Turns the steps of a debugger install into one smooth progress fraction. */
object InstallProgress {

  /** How many steps an install has: look up, download, check, unpack and create launchers. */
  const val STEPS = 5

  /**
   * Works out how far the whole install is while the download step runs.
   *
   * The finished steps count fully and the download counts by the share of bytes that arrived, so the bar moves
   * smoothly instead of jumping.
   *
   * @param step the number of the current step (the first step is 1).
   * @param total the number of steps.
   * @param bytes how many bytes arrived so far.
   * @param size the size of the download, or a value of 0 or less when it is not known.
   * @return a fraction between 0 and 1, or `null` when the numbers do not allow one.
   */
  fun fraction(step: Int, total: Int, bytes: Long, size: Long): Double? {
    if (total <= 0 || step <= 0 || size <= 0 || bytes < 0) return null
    val share = (bytes.toDouble() / size).coerceIn(0.0, 1.0)
    return ((step - 1) + share) / total
  }
}

/**
 * What an install of the debugger did.
 *
 * @property action `installed`, `upgraded`, `reinstalled` or `unchanged`.
 * @property version the release tag that is installed, or an empty string when it is not known.
 * @property executable the path to start the program with.
 * @property notes things the person should know, such as a launcher that could not be created.
 */
data class InstallResult(
  val action: String,
  val version: String,
  val executable: String,
  val notes: List<String> = emptyList(),
) {

  /** A short text for a notification, with the notes on their own lines. */
  fun summary(): String {
    val what = if (version.isEmpty()) "The debugger" else "The debugger $version"
    val first = when (action) {
      UNCHANGED -> "$what is already installed."
      UPGRADED -> "$what was upgraded."
      REINSTALLED -> "$what was reinstalled."
      else -> "$what was installed."
    }
    return (listOf(first) + notes).joinToString("\n")
  }

  companion object {
    const val INSTALLED = "installed"
    const val UPGRADED = "upgraded"
    const val REINSTALLED = "reinstalled"
    const val UNCHANGED = "unchanged"
  }
}
