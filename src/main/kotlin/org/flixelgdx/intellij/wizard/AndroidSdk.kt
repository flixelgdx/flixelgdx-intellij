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
package org.flixelgdx.intellij.wizard

import com.intellij.openapi.util.SystemInfo
import com.intellij.util.EnvironmentUtil
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Everything the Android SDK search reads from the outside world, so tests can replace it.
 *
 * @property getenv reads an environment variable, or returns `null` when it is not set.
 * @property home the user's home folder.
 * @property windows whether the search runs on Windows.
 * @property mac whether the search runs on macOS.
 * @property isDir tells whether a path is an existing folder.
 */
class SdkEnvironment(
  val getenv: (String) -> String?,
  val home: Path,
  val windows: Boolean,
  val mac: Boolean,
  val isDir: (Path) -> Boolean = { Files.isDirectory(it) },
) {
  companion object {
    /** The real environment of this IDE process. */
    fun system() = SdkEnvironment(
      getenv = { EnvironmentUtil.getValue(it) },
      home = Paths.get(System.getProperty("user.home")),
      windows = SystemInfo.isWindows,
      mac = SystemInfo.isMac,
    )
  }
}

/**
 * Looks for an Android SDK, so the wizard can warn before someone picks Android without one.
 *
 * The Android build needs an SDK, and `flxgdx` skips the Android module while it cannot find one. The search checks
 * `ANDROID_HOME`, then `ANDROID_SDK_ROOT`, then the folder where Android Studio installs the SDK by default.
 */
object AndroidSdk {

  /**
   * Finds the Android SDK folder.
   *
   * @return the first folder that exists, or `null` when there is none. Touches the disk, so call it off the EDT.
   */
  fun find(env: SdkEnvironment): Path? {
    for (key in listOf("ANDROID_HOME", "ANDROID_SDK_ROOT")) {
      val value = env.getenv(key)?.trim().orEmpty()
      if (value.isEmpty()) continue
      val path = runCatching { Paths.get(value) }.getOrNull() ?: continue
      if (env.isDir(path)) return path
    }
    val default = defaultFolder(env)
    return default?.takeIf { env.isDir(it) }
  }

  /** The folder where Android Studio installs the SDK on this operating system, whether or not it exists. */
  fun defaultFolder(env: SdkEnvironment): Path? = when {
    env.windows -> env.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }
      ?.let { runCatching { Paths.get(it, "Android", "Sdk") }.getOrNull() }
    env.mac -> env.home.resolve("Library").resolve("Android").resolve("sdk")
    else -> env.home.resolve("Android").resolve("Sdk")
  }
}
