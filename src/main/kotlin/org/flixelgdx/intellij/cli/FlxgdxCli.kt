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
import com.intellij.openapi.progress.ProgressIndicator
import java.nio.file.Files
import java.nio.file.Path

/** The health of the installed `flxgdx`. */
sealed interface CliStatus {
  /** Human-readable summary, for the settings page. */
  fun describe(): String

  /** `flxgdx` was found at [path] and is new enough. */
  data class Ok(val path: Path, val version: CliVersion) : CliStatus {
    override fun describe() = "flxgdx $version found at $path."
  }

  /** `flxgdx` could not be found. */
  data object Missing : CliStatus {
    override fun describe() = "flxgdx was not found."
  }

  /** `flxgdx` at [path] is older than [minimum]. */
  data class TooOld(val path: Path, val found: CliVersion, val minimum: CliVersion) : CliStatus {
    override fun describe() = "flxgdx $found is too old. Version $minimum or newer is needed."
  }

  /** `flxgdx` was found, but running `version` failed for [reason]. */
  data class Broken(val path: Path, val reason: String) : CliStatus {
    override fun describe() = "flxgdx could not be run: $reason"
  }
}

/**
 * Checks that the installed `flxgdx` exists and is at least [CliVersion.MINIMUM], by running `flxgdx version --json`.
 *
 * A good answer is cached per executable file (keyed by its path and modification time), so the version command runs
 * once, not before every action.
 */
@Service(Service.Level.APP)
class FlxgdxCli {

  private val lock = Any()
  private var cachedKey: String? = null
  private var cached: CliStatus.Ok? = null

  /** Finds `flxgdx` and checks its version. Blocks, so call it off the EDT. */
  fun status(indicator: ProgressIndicator? = null): CliStatus {
    val located = service<FlxgdxLocator>().find() ?: return CliStatus.Missing
    return probe(located.path, indicator)
  }

  /** Checks the `flxgdx` at [exe] without searching for it. Blocks, so call it off the EDT. */
  fun probe(exe: Path, indicator: ProgressIndicator? = null): CliStatus {
    if (!Files.isRegularFile(exe)) return CliStatus.Missing
    val key = "$exe|${runCatching { Files.getLastModifiedTime(exe).toMillis() }.getOrDefault(0L)}"
    synchronized(lock) {
      if (key == cachedKey) cached?.let { return it }
    }

    val result = service<FlxgdxRunner>().run(
      listOf("version"),
      indicator = indicator,
      executable = exe,
      timeoutMs = VERSION_TIMEOUT_MS,
    )
    val status = interpret(exe, result, CliVersion.MINIMUM)
    if (status is CliStatus.Ok) {
      synchronized(lock) {
        cachedKey = key
        cached = status
      }
    }
    return status
  }

  /** Forgets the cached version check. */
  fun invalidate() {
    synchronized(lock) {
      cachedKey = null
      cached = null
    }
  }

  companion object {
    private const val VERSION_TIMEOUT_MS = 15_000L

    /** Turns the result of `flxgdx version --json` into a status. Pure, so it is easy to test. */
    fun interpret(exe: Path, result: CliResult, minimum: CliVersion): CliStatus {
      if (!result.isSuccess) return CliStatus.Broken(exe, result.describe())
      val text = result.data.stringAt("version")
      val version = text?.let { CliVersion.parse(it) }
        ?: return CliStatus.Broken(exe, "the version output could not be understood")
      return if (version < minimum) CliStatus.TooOld(exe, version, minimum) else CliStatus.Ok(exe, version)
    }
  }
}
