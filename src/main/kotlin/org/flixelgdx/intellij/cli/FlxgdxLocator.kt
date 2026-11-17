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

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.EnvironmentUtil
import org.flixelgdx.intellij.settings.FlxgdxSettings
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/** Where a located `flxgdx` came from. */
enum class LocationSource { SETTINGS, PATH, COMMON_FOLDER }

/** A `flxgdx` executable that was found, and the place it was found. */
data class LocatedCli(val path: Path, val source: LocationSource)

/**
 * Everything the search for `flxgdx` reads from the outside world, so tests can replace it.
 *
 * @property pathVariable the value of the `PATH` variable, or `null` when there is none.
 * @property home the user's home folder.
 * @property windows whether the search runs on Windows (executables end in `.exe`).
 * @property mac whether the search runs on macOS.
 * @property gopath the value of `GOPATH`, or `null`.
 */
data class LocatorEnvironment(
  val pathVariable: String?,
  val home: Path,
  val windows: Boolean,
  val mac: Boolean,
  val gopath: String? = null,
) {
  companion object {
    /** The real environment of this IDE process. */
    fun system() = LocatorEnvironment(
      pathVariable = EnvironmentUtil.getValue("PATH"),
      home = Paths.get(System.getProperty("user.home")),
      windows = SystemInfo.isWindows,
      mac = SystemInfo.isMac,
      gopath = EnvironmentUtil.getValue("GOPATH"),
    )
  }
}

/**
 * Finds the `flxgdx` executable.
 *
 * Think of it as asking three places in a fixed order: first the path the user typed in the settings, then every
 * folder on `PATH`, then the folders where `flxgdx` usually lands after an install. The first hit wins. The answer is
 * cached, and the cache is dropped when the settings path changes or when [invalidate] is called.
 */
@Service(Service.Level.APP)
class FlxgdxLocator {

  private val lock = Any()
  private var cachedKey: String? = null
  private var cached: LocatedCli? = null

  /** Returns the cached location, searching first if needed. Safe to call from any thread, but touches the disk. */
  fun find(): LocatedCli? {
    val key = service<FlxgdxSettings>().executablePath
    synchronized(lock) {
      if (cachedKey == key && cached != null) return cached
    }
    val found = locate(key, LocatorEnvironment.system())
    synchronized(lock) {
      cachedKey = key
      cached = found
    }
    return found
  }

  /** Forgets the cached location so the next [find] searches again. */
  fun invalidate() {
    synchronized(lock) {
      cachedKey = null
      cached = null
    }
  }

  companion object {
    /** Executable name without any platform suffix. */
    const val NAME = "flxgdx"

    /**
     * Runs the search without any caching.
     *
     * @param settingsPath the path from the settings; blank means automatic. It may name the executable or the
     * folder that holds it.
     * @param env the outside world to read from.
     */
    fun locate(settingsPath: String, env: LocatorEnvironment): LocatedCli? {
      val exeName = if (env.windows) "$NAME.exe" else NAME

      resolveSetting(settingsPath.trim(), exeName)?.let { return LocatedCli(it, LocationSource.SETTINGS) }

      env.pathVariable?.split(File.pathSeparatorChar)?.forEach { dir ->
        if (dir.isNotBlank()) {
          executableIn(dir, exeName)?.let { return LocatedCli(it, LocationSource.PATH) }
        }
      }

      for (dir in commonFolders(env)) {
        executableIn(dir.toString(), exeName)?.let { return LocatedCli(it, LocationSource.COMMON_FOLDER) }
      }
      return null
    }

    /** The install folders that are checked after `PATH`, in order. */
    fun commonFolders(env: LocatorEnvironment): List<Path> {
      val list = ArrayList<Path>()
      env.gopath?.split(File.pathSeparatorChar)?.forEach {
        if (it.isNotBlank()) list.add(Paths.get(it, "bin"))
      }
      list.add(env.home.resolve("go").resolve("bin"))
      list.add(env.home.resolve(".local").resolve("bin"))
      if (env.mac) {
        list.add(Paths.get("/opt/homebrew/bin"))
        list.add(Paths.get("/usr/local/bin"))
      }
      if (env.windows) {
        list.add(env.home.resolve("scoop").resolve("shims"))
      }
      return list
    }

    private fun resolveSetting(setting: String, exeName: String): Path? {
      if (setting.isEmpty()) return null
      val path = runCatching { Paths.get(setting) }.getOrNull() ?: return null
      if (Files.isDirectory(path)) return executableIn(path.toString(), exeName)
      return path.takeIf { Files.isRegularFile(it) && Files.isExecutable(it) }
    }

    private fun executableIn(dir: String, exeName: String): Path? {
      val path = runCatching { Paths.get(dir, exeName) }.getOrNull() ?: return null
      return path.takeIf { Files.isRegularFile(it) && Files.isExecutable(it) }
    }
  }
}
