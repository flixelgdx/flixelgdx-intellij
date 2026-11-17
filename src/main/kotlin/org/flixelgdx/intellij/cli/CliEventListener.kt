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

/**
 * Receives the events of a running `flxgdx` command, one callback per event type.
 *
 * Every method has an empty default, so implement only what you need. Callbacks arrive on a background thread
 * while the command runs, never on the EDT. Hop to the EDT yourself before touching Swing.
 */
interface CliEventListener {
  /** A step started. [total] is `null` when the number of steps is unknown. */
  fun onProgress(step: Int, total: Int?, message: String) {}

  /** Extra information arrived. [data] may hold details such as the log level or the stream name. */
  fun onLog(message: String, data: JsonObject?) {}

  /** The command reported a problem but carried on. */
  fun onWarning(message: String) {}

  /** The command produced its result. [data] has a command-specific shape and may be `null`. */
  fun onResult(data: JsonObject?) {}

  /** The command failed with a stable [code] (may be `null` for very old versions) and a [message]. */
  fun onError(code: String?, message: String) {}
}
