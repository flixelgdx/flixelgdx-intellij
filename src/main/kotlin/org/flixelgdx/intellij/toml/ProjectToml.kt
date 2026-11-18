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
package org.flixelgdx.intellij.toml

/**
 * A tiny reader for the one value of `project.toml` that the run configurations need: the enabled platforms.
 *
 * It works on plain text, so it can run on a background thread at startup before the IDE has parsed anything.
 *
 * It is deliberately not a TOML parser. It only understands a top-level `platforms = ["desktop", "html5"]` line (the
 * list may span several lines), and it gives up with `null` as soon as anything looks unusual, so the plugin never
 * acts on a guess.
 */
object ProjectToml {

  /**
   * Returns the platforms listed by the top-level `platforms` key of [text], without duplicates and in file order.
   *
   * It returns `null` (meaning "do not trust this") when the key is missing or appears twice, when the value is not
   * a list of plain quoted strings, or when the list is not closed. Keys inside a `[table]` are ignored. Unknown
   * platform names are kept; callers decide what to do with them.
   */
  fun platforms(text: String): List<String>? {
    var inTable = false
    var value: StringBuilder? = null
    var found = false
    var closed = false
    for (raw in text.lineSequence()) {
      val line = stripComment(raw).trim()
      if (value != null && !closed) {
        value.append(' ').append(line)
        closed = line.contains(']')
        continue
      }
      if (line.isEmpty()) continue
      if (line.startsWith("[")) {
        inTable = true
        continue
      }
      if (inTable) continue
      val eq = line.indexOf('=')
      if (eq < 0 || line.substring(0, eq).trim() != KEY) continue
      if (found) return null
      found = true
      value = StringBuilder(line.substring(eq + 1).trim())
      closed = value.contains(']')
    }
    return value?.let { parseList(it.toString()) }
  }

  private fun parseList(value: String): List<String>? {
    val text = value.trim()
    if (!text.startsWith("[") || !text.endsWith("]")) return null
    val inner = text.substring(1, text.length - 1).trim()
    if (inner.isEmpty()) return emptyList()
    val result = ArrayList<String>()
    for (part in inner.split(',')) {
      val item = part.trim()
      if (item.isEmpty()) continue
      if (item.length < 2) return null
      val quote = item[0]
      if ((quote != '"' && quote != '\'') || item.last() != quote) return null
      val name = item.substring(1, item.length - 1)
      if (name.isEmpty() || name.any { it == '"' || it == '\'' || it == '\\' || it == '[' || it == ']' }) return null
      if (name !in result) result += name
    }
    return result
  }

  /** Cuts a trailing `#` comment, ignoring a `#` inside quotes. */
  private fun stripComment(line: String): String {
    var quote = 0.toChar()
    for (i in line.indices) {
      val c = line[i]
      if (quote != 0.toChar()) {
        if (c == quote) quote = 0.toChar()
      } else if (c == '"' || c == '\'') {
        quote = c
      } else if (c == '#') {
        return line.substring(0, i)
      }
    }
    return line
  }

  private const val KEY = "platforms"
}
