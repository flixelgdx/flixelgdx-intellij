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

/**
 * A version number such as `1.2.3`, used to compare the installed `flxgdx` against the minimum the plugin needs.
 *
 * Only the numeric core (major, minor, patch) and an optional pre-release suffix are understood. A pre-release
 * (for example `1.0.0-rc1`) sorts below the same release without a suffix, the way semantic versioning says.
 *
 * Example: `CliVersion.parse("v1.2.0") >= CliVersion(1, 0, 0)` is `true`.
 */
data class CliVersion(val major: Int, val minor: Int, val patch: Int, val preRelease: String = "") :
  Comparable<CliVersion> {

  override fun compareTo(other: CliVersion): Int {
    if (major != other.major) return major.compareTo(other.major)
    if (minor != other.minor) return minor.compareTo(other.minor)
    if (patch != other.patch) return patch.compareTo(other.patch)
    if (preRelease == other.preRelease) return 0
    if (preRelease.isEmpty()) return 1
    if (other.preRelease.isEmpty()) return -1
    return preRelease.compareTo(other.preRelease)
  }

  override fun toString(): String = "$major.$minor.$patch" + if (preRelease.isEmpty()) "" else "-$preRelease"

  companion object {
    /** The oldest `flxgdx` the plugin works with. */
    val MINIMUM = CliVersion(1, 0, 0)

    private val PATTERN = Regex("""^v?(\d+)(?:\.(\d+))?(?:\.(\d+))?(?:-([0-9A-Za-z.-]+))?(?:\+.*)?$""")

    /** Reads [text] as a version, or returns `null` when it does not look like one. */
    fun parse(text: String): CliVersion? {
      val m = PATTERN.matchEntire(text.trim()) ?: return null
      val (major, minor, patch, pre) = m.destructured
      return CliVersion(
        major.toIntOrNull() ?: return null,
        minor.ifEmpty { "0" }.toIntOrNull() ?: return null,
        patch.ifEmpty { "0" }.toIntOrNull() ?: return null,
        pre,
      )
    }
  }
}
