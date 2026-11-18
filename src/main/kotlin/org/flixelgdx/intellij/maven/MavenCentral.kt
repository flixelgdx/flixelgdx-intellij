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
package org.flixelgdx.intellij.maven

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.util.io.HttpRequests
import java.io.IOException

/**
 * Finds the latest release of an artifact on Maven Central, by reading its `maven-metadata.xml`.
 *
 * Why Maven Central and not GitHub? The framework and its extensions are published there, so the version that Maven
 * Central knows is exactly the version Gradle can download. It also needs no API token and has no rate limit.
 *
 * Example:
 * ```
 * val version = MavenCentral.latest("org.flixelgdx", "flixelgdx-core", indicator) // such as "0.7.2"
 * ```
 */
object MavenCentral {

  /** The base address of Maven Central. */
  const val BASE_URL = "https://repo1.maven.org/maven2"

  /** The Maven group of the framework and its extensions. */
  const val FRAMEWORK_GROUP = "org.flixelgdx"

  /** The artifact whose releases define the version of the framework. */
  const val FRAMEWORK_CORE = "flixelgdx-core"

  /** The artifact whose releases define the version of the Kotlin extension. */
  const val KTX_CORE = "flixelgdx-ktx-core"

  private const val TIMEOUT_MS = 15_000

  /**
   * Downloads the metadata of `group:artifact` and returns its latest release.
   *
   * It blocks on the network, so call it from a background task.
   *
   * @param indicator the progress of the task, checked for cancellation; `null` when there is none.
   * @throws IOException when the metadata cannot be downloaded or holds no release.
   */
  fun latest(group: String, artifact: String, indicator: ProgressIndicator? = null): String {
    val text = HttpRequests.request(metadataUrl(group, artifact))
      .productNameAsUserAgent()
      .connectTimeout(TIMEOUT_MS)
      .readTimeout(TIMEOUT_MS)
      .readString(indicator)
    return MavenMetadata.latestRelease(text) ?: throw IOException("Maven Central lists no release of $group:$artifact")
  }

  /** The address of the `maven-metadata.xml` of `group:artifact`. */
  fun metadataUrl(group: String, artifact: String): String =
    "$BASE_URL/${group.replace('.', '/')}/$artifact/maven-metadata.xml"
}

/** Reads the parts of a `maven-metadata.xml` that the plugin needs, without an XML library. */
object MavenMetadata {

  private val RELEASE = Regex("<release>\\s*([^<\\s]+)\\s*</release>")
  private val VERSION = Regex("<version>\\s*([^<\\s]+)\\s*</version>")

  /**
   * Returns the latest release listed in [xml].
   *
   * The `<release>` element wins when it looks like a version. Otherwise the highest of the `<version>` entries that
   * start with a digit is used, so a stray branch build such as `master` is never picked.
   *
   * @return the version, or `null` when there is none.
   */
  fun latestRelease(xml: String): String? {
    RELEASE.find(xml)?.groupValues?.get(1)?.takeIf(::isRelease)?.let { return it }
    return VERSION.findAll(xml).map { it.groupValues[1] }.filter(::isRelease).maxWithOrNull(VersionOrder)
  }

  /** Whether [version] looks like a release: it starts with a digit and is not a snapshot. */
  fun isRelease(version: String): Boolean =
    version.isNotEmpty() && version[0].isDigit() && !version.endsWith("-SNAPSHOT")
}

/**
 * Orders versions such as `0.7.2` and `0.10.0` the way people expect: number by number, so `0.10.0` is newer than
 * `0.9.1`. A version with a suffix such as `1.0.0-beta` comes before `1.0.0`.
 */
object VersionOrder : Comparator<String> {

  override fun compare(a: String, b: String): Int {
    val (aMain, aSuffix) = split(a)
    val (bMain, bSuffix) = split(b)
    for (i in 0 until maxOf(aMain.size, bMain.size)) {
      val c = aMain.getOrElse(i) { 0 }.compareTo(bMain.getOrElse(i) { 0 })
      if (c != 0) return c
    }
    return when {
      aSuffix == bSuffix -> 0
      aSuffix.isEmpty() -> 1
      bSuffix.isEmpty() -> -1
      else -> aSuffix.compareTo(bSuffix)
    }
  }

  private fun split(version: String): Pair<List<Long>, String> {
    val dash = version.indexOfFirst { it == '-' || it == '+' }
    val main = if (dash < 0) version else version.substring(0, dash)
    val suffix = if (dash < 0) "" else version.substring(dash + 1)
    return main.split('.').map { it.toLongOrNull() ?: 0L } to suffix
  }
}
