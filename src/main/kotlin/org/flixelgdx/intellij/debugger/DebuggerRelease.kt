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
 * Where the releases of the FlixelGDX Debugger are, and which file of a release fits this computer.
 *
 * The plugin downloads straight from the release pages of GitHub (`releases/latest/download/<file>`), which needs no
 * API token and has no rate limit. Think of it as a shop with a fixed address: the latest release is always behind
 * the same door, and only the name of the box on the shelf depends on the computer.
 */
object DebuggerRelease {

  /** The release page of the debugger repository. */
  const val RELEASES_URL = "https://github.com/flixelgdx/flixelgdx-debugger/releases"

  /** Redirects to the page of the latest release, whose address ends with its tag. */
  const val LATEST_URL = "$RELEASES_URL/latest"

  /** The optional file of a release that lists the SHA-256 of the other files, like `sha256sum` prints them. */
  const val CHECKSUMS = "checksums.txt"

  private val TAG_IN_URL = Regex("/releases/(?:tag|download)/([^/?#]+)")
  private val HASH = Regex("[0-9a-fA-F]{64}")

  /**
   * The name of the release file for an operating system and processor, such as `flixelgdx-debugger-linux-x64.zip`.
   *
   * @param osName the value of the `os.name` system property.
   * @param arch the value of the `os.arch` system property.
   * @return the file name, or `null` when the debugger has no build for this computer.
   */
  fun asset(osName: String, arch: String): String? {
    val os = osName.lowercase()
    val cpu = when (arch.lowercase()) {
      "amd64", "x86_64", "x64" -> "x64"
      "aarch64", "arm64" -> "arm64"
      else -> return null
    }
    val platform = when {
      os.startsWith("windows") -> "win-$cpu"
      os.startsWith("mac") || os.startsWith("darwin") -> if (cpu == "arm64") "osx-arm64" else return null
      os.startsWith("linux") -> "linux-$cpu"
      else -> return null
    }
    return "flixelgdx-debugger-$platform.zip"
  }

  /** The address that always serves [file] of the latest release. */
  fun latestDownloadUrl(file: String): String = "$LATEST_URL/download/$file"

  /** The release tag in a release address such as `.../releases/tag/v1.0.1`, or `null` when there is none. */
  fun tagFrom(url: String): String? = TAG_IN_URL.find(url)?.groupValues?.get(1)

  /**
   * Reads a `checksums.txt` with lines such as `<hash>  <name>` or `<hash> *<name>`.
   *
   * @return the lowercase SHA-256 of every file by name. Lines in another format are skipped.
   */
  fun parseChecksums(text: String): Map<String, String> {
    val out = HashMap<String, String>()
    for (line in text.lines()) {
      val fields = line.trim().split(Regex("\\s+"))
      if (fields.size != 2 || !HASH.matches(fields[0])) continue
      out[fields[1].removePrefix("*")] = fields[0].lowercase()
    }
    return out
  }
}

/**
 * The folders the debugger is installed into, per operating system. They are the same folders earlier FlixelGDX
 * tools used, so an existing install is found and upgraded in place.
 *
 * - Linux: `$XDG_DATA_HOME/flixelgdx/debugger`, or `~/.local/share/flixelgdx/debugger`.
 * - Windows: `%LOCALAPPDATA%\FlixelGDX\Debugger`, or `~\AppData\Local\FlixelGDX\Debugger`.
 * - macOS: `~/Library/Application Support/FlixelGDX/Debugger`.
 */
object DebuggerPaths {

  /** The name of the small file in the install folder that remembers the installed release tag. */
  const val VERSION_FILE = ".flixelgdx-version"

  /** The default install folder on the computer described by [env]. */
  fun installDir(env: DebuggerEnvironment): Path = when {
    env.windows -> {
      val base = env.getenv("LOCALAPPDATA")?.takeIf { it.isNotEmpty() }?.let { Path.of(it) }
        ?: env.home.resolve("AppData").resolve("Local")
      base.resolve("FlixelGDX").resolve("Debugger")
    }
    env.mac -> env.home.resolve("Library").resolve("Application Support").resolve("FlixelGDX").resolve("Debugger")
    else -> dataHome(env).resolve("flixelgdx").resolve("debugger")
  }

  /** The executable inside [dir]. */
  fun executable(dir: Path, env: DebuggerEnvironment): Path =
    dir.resolve(if (env.windows) "${DebuggerDetector.EXECUTABLE}.exe" else DebuggerDetector.EXECUTABLE)

  /** The freedesktop data folder: `$XDG_DATA_HOME` when it is an absolute path, otherwise `~/.local/share`. */
  fun dataHome(env: DebuggerEnvironment): Path {
    val xdg = env.getenv("XDG_DATA_HOME")
    if (!xdg.isNullOrEmpty()) {
      val path = runCatching { Path.of(xdg) }.getOrNull()
      if (path != null && path.isAbsolute) return path
    }
    return env.home.resolve(".local").resolve("share")
  }

  /** The folder for the launcher link on Linux and macOS: `~/.local/bin`. */
  fun binDir(env: DebuggerEnvironment): Path = env.home.resolve(".local").resolve("bin")
}
