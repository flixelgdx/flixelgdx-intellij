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

import java.nio.file.Path

/**
 * What `flxgdx` remembers about one program it installed, as read from `state.toml`.
 *
 * @property version the release tag, such as `v1.0.1`, or `null` when the file does not say.
 * @property dir the folder the program was unpacked into, or `null`.
 * @property executable the path to start the program with.
 */
data class InstalledTool(val version: String?, val dir: String?, val executable: String)

/**
 * A small, forgiving reader for `state.toml`, the file in which `flxgdx` records the programs it installed.
 *
 * The file is written by the command-line tool and looks like this:
 * ```
 * [tools]
 * [tools.debugger]
 * version = 'v1.0.1'
 * dir = '/home/me/.local/share/flixelgdx/debugger'
 * executable = '/home/me/.local/share/flixelgdx/debugger/FlixelDebugger.App'
 * shortcuts = ['/home/me/.local/bin/flixelgdx-debugger']
 * ```
 *
 * This is deliberately not a full TOML parser. It reads the `[tools.<name>]` tables and the plain string values in
 * them (single quoted, or double quoted with the usual escapes), skips everything it does not understand, and returns
 * `null` instead of guessing when the entry it wants is missing. Unknown keys and tables are ignored, so newer
 * versions of `flxgdx` keep working.
 */
object StateToml {

  /**
   * Returns the entry of the tool called [name], or `null` when the file has no such table or the table has no
   * `executable`. When the table appears twice, the last one wins, like a later write would.
   */
  fun tool(text: String, name: String): InstalledTool? {
    var current: String? = null
    val values = HashMap<String, MutableMap<String, String>>()
    for (raw in text.lineSequence()) {
      val line = stripComment(raw).trim()
      if (line.isEmpty()) continue
      if (line.startsWith("[")) {
        current = tableName(line)
        continue
      }
      val table = current ?: continue
      val eq = line.indexOf('=')
      if (eq <= 0) continue
      val key = unquoteKey(line.substring(0, eq).trim()) ?: continue
      val value = readString(line.substring(eq + 1).trim()) ?: continue
      values.getOrPut(table) { HashMap() }[key] = value
    }
    val table = values[name] ?: return null
    val exe = table["executable"]?.takeIf { it.isNotBlank() } ?: return null
    return InstalledTool(table["version"]?.takeIf { it.isNotBlank() }, table["dir"]?.takeIf { it.isNotBlank() }, exe)
  }

  /** `[tools.debugger]` gives `debugger`. Anything that is not a table of `tools` gives `null`. */
  private fun tableName(line: String): String? {
    if (line.startsWith("[[") || !line.endsWith("]")) return null
    val inner = line.substring(1, line.length - 1).trim()
    val dot = inner.indexOf('.')
    if (dot < 0) return null
    if (unquoteKey(inner.substring(0, dot).trim()) != "tools") return null
    return unquoteKey(inner.substring(dot + 1).trim())
  }

  private fun unquoteKey(key: String): String? {
    if (key.isEmpty()) return null
    val first = key[0]
    if (first == '"' || first == '\'') {
      if (key.length < 2 || key.last() != first) return null
      return key.substring(1, key.length - 1)
    }
    return key.takeIf { k -> k.all { it.isLetterOrDigit() || it == '_' || it == '-' } }
  }

  /** Reads a whole value that is one quoted string. Arrays, numbers and anything else give `null`. */
  private fun readString(value: String): String? {
    if (value.length < 2) return null
    val quote = value[0]
    if (quote == '\'') {
      return value.takeIf { it.last() == '\'' }?.substring(1, value.length - 1)
    }
    if (quote != '"' || value.last() != '"') return null
    val out = StringBuilder()
    var i = 1
    val end = value.length - 1
    while (i < end) {
      val c = value[i]
      if (c != '\\') {
        out.append(c)
        i++
        continue
      }
      if (i + 1 >= end) return null
      when (val next = value[i + 1]) {
        '\\', '"' -> out.append(next)
        'n' -> out.append('\n')
        't' -> out.append('\t')
        'r' -> out.append('\r')
        'u' -> {
          if (i + 6 > end) return null
          val code = value.substring(i + 2, i + 6).toIntOrNull(HEX) ?: return null
          out.append(code.toChar())
          i += 4
        }
        else -> return null
      }
      i += 2
    }
    return out.toString()
  }

  /** Cuts a trailing `#` comment, ignoring a `#` inside quotes. */
  private fun stripComment(line: String): String {
    var quote = 0.toChar()
    var i = 0
    while (i < line.length) {
      val c = line[i]
      if (quote != 0.toChar()) {
        if (quote == '"' && c == '\\') i++ else if (c == quote) quote = 0.toChar()
      } else if (c == '"' || c == '\'') {
        quote = c
      } else if (c == '#') {
        return line.substring(0, i)
      }
      i++
    }
    return line
  }

  private const val HEX = 16
}

/** Where the user config folder is, the way Go's `os.UserConfigDir` decides it on each operating system. */
object ConfigDir {

  /**
   * Resolves the folder that holds the `flxgdx` folder (and so `state.toml`).
   *
   * - Linux and other Unix: `$XDG_CONFIG_HOME` when set (it must be an absolute path), otherwise `~/.config`.
   * - macOS: `~/Library/Application Support`.
   * - Windows: `%AppData%`.
   *
   * @param windows whether this is Windows.
   * @param mac whether this is macOS.
   * @param getenv reads an environment variable, `null` when it is not set.
   * @param home the user's home folder.
   * @return the folder, or `null` when it cannot be worked out (the same cases in which Go reports an error).
   */
  fun resolve(windows: Boolean, mac: Boolean, getenv: (String) -> String?, home: Path): Path? {
    if (windows) {
      return getenv("AppData")?.takeIf { it.isNotEmpty() }?.let { runCatching { Path.of(it) }.getOrNull() }
    }
    if (mac) return home.resolve("Library").resolve("Application Support")
    val xdg = getenv("XDG_CONFIG_HOME")
    if (xdg.isNullOrEmpty()) return home.resolve(".config")
    val path = runCatching { Path.of(xdg) }.getOrNull() ?: return null
    return path.takeIf { it.isAbsolute }
  }

  /** The path of `state.toml` inside [configDir]: `<configDir>/flxgdx/state.toml`. */
  fun statePath(configDir: Path): Path = configDir.resolve("flxgdx").resolve("state.toml")
}
