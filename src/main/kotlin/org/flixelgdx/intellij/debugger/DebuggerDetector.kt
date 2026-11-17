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

import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/** The place a debugger executable was found. */
enum class DebuggerSource { SETTINGS, CLI_STATE, PATH }

/**
 * A FlixelGDX Debugger executable that was found.
 *
 * @property path the executable to start.
 * @property version the release tag the CLI recorded, or `null` when it is not known (for example from `PATH`).
 * @property source where it was found.
 */
data class DetectedDebugger(val path: Path, val version: String?, val source: DebuggerSource)

/**
 * Everything the search for the debugger reads from the outside world, so tests can replace it.
 *
 * @property pathVariable the value of the `PATH` variable, or `null` when there is none.
 * @property home the user's home folder.
 * @property windows whether the search runs on Windows (executables end in `.exe`).
 * @property mac whether the search runs on macOS.
 * @property getenv reads an environment variable, `null` when it is not set.
 */
data class DebuggerEnvironment(
  val pathVariable: String?,
  val home: Path,
  val windows: Boolean,
  val mac: Boolean,
  val getenv: (String) -> String?,
)

/**
 * Finds the FlixelGDX Debugger.
 *
 * Think of it as asking three people in a fixed order: first the path the user typed in the settings, then the record
 * that `flxgdx install debugger` left in `state.toml`, then every folder on `PATH` for `flixelgdx-debugger`. The
 * first one that points at a real executable wins. It touches the disk, so call it off the EDT.
 */
object DebuggerDetector {

  /** The name of the launcher that `flxgdx` puts on `PATH`. */
  const val COMMAND = "flixelgdx-debugger"

  /** The name of the program inside the release (without `.exe`). */
  const val EXECUTABLE = "FlixelDebugger.App"

  /**
   * Runs the search.
   *
   * @param configured the path from the settings; blank means automatic. It may name the executable or the folder
   * that holds it.
   * @param env the outside world to read from.
   */
  fun detect(configured: String, env: DebuggerEnvironment): DetectedDebugger? {
    val exeName = if (env.windows) "$EXECUTABLE.exe" else EXECUTABLE

    resolveSetting(configured.trim(), exeName)?.let { return DetectedDebugger(it, null, DebuggerSource.SETTINGS) }

    recorded(env)?.let { entry ->
      val path = runCatching { Path.of(entry.executable) }.getOrNull()
      if (path != null && isExecutable(path)) return DetectedDebugger(path, entry.version, DebuggerSource.CLI_STATE)
    }

    val command = if (env.windows) "$COMMAND.exe" else COMMAND
    env.pathVariable?.split(File.pathSeparatorChar)?.forEach { dir ->
      if (dir.isNotBlank()) {
        val path = runCatching { Path.of(dir, command) }.getOrNull()
        if (path != null && isExecutable(path)) return DetectedDebugger(path, null, DebuggerSource.PATH)
      }
    }
    return null
  }

  /** The entry for the debugger in the `state.toml` of the CLI, or `null` when there is none or it is unreadable. */
  fun recorded(env: DebuggerEnvironment): InstalledTool? {
    val config = ConfigDir.resolve(env.windows, env.mac, env.getenv, env.home) ?: return null
    val text = runCatching { Files.readString(ConfigDir.statePath(config)) }.getOrNull() ?: return null
    return StateToml.tool(text, "debugger")
  }

  private fun resolveSetting(setting: String, exeName: String): Path? {
    if (setting.isEmpty()) return null
    val path = runCatching { Path.of(setting) }.getOrNull() ?: return null
    if (Files.isDirectory(path)) return path.resolve(exeName).takeIf { isExecutable(it) }
    return path.takeIf { isExecutable(it) }
  }

  private fun isExecutable(path: Path) = Files.isRegularFile(path) && Files.isExecutable(path)
}
