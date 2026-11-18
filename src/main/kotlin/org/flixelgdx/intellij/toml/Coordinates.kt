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
 * Maven coordinates of a library, as typed by a person: `group:artifact:version`.
 *
 * @property group the Maven group, such as `org.flixelgdx`.
 * @property artifact the artifact name, such as `flixelgdx-ui`.
 * @property version the version, such as `1.0.0`.
 */
data class Coordinates(val group: String, val artifact: String, val version: String) {

  /** The `group:artifact` part that the version catalog stores as `module`. */
  val module: String get() = "$group:$artifact"

  override fun toString() = "$group:$artifact:$version"

  companion object {
    private val PART = Regex("[A-Za-z0-9_.\\-]+")
    private val VERSION = Regex("[A-Za-z0-9_.+\\-]+")

    /**
     * Reads `group:artifact:version`, trimming spaces around each part.
     *
     * @return the coordinates, or `null` when [text] does not have exactly three non-empty parts made of the
     * characters Maven allows.
     */
    fun parse(text: String): Coordinates? {
      val parts = text.trim().split(':').map { it.trim() }
      if (parts.size != 3) return null
      val (group, artifact, version) = parts
      if (!PART.matches(group) || !PART.matches(artifact) || !VERSION.matches(version)) return null
      return Coordinates(group, artifact, version)
    }
  }
}

/** Checks the names used as keys in the version catalog. */
object CatalogNames {

  private val ALIAS = Regex("[a-z][a-zA-Z0-9_.\\-]*")

  /**
   * Checks a library alias the way Gradle does: it starts with a lowercase letter and holds only letters, digits,
   * `-`, `_` and `.`, and does not end with one of the separators.
   *
   * @return a sentence describing the problem, or `null` when the alias is fine.
   */
  fun aliasProblem(alias: String): String? = when {
    alias.isEmpty() -> "The alias must not be empty."
    !ALIAS.matches(alias) ->
      "The alias must start with a lowercase letter and use only letters, digits, '-', '_' and '.'."
    alias.last() in "-_." -> "The alias must not end with '${alias.last()}'."
    else -> null
  }

  /** Turns an artifact name into an alias, such as `flixelgdx-ui` for `flixelgdx-ui`, or `my-lib` for `My_Lib`. */
  fun aliasFor(artifact: String): String {
    val cleaned = artifact.lowercase().map { if (it.isLetterOrDigit() || it == '-' || it == '.') it else '-' }
      .joinToString("").trim('-', '.')
    return if (cleaned.isEmpty() || !cleaned[0].isLetter()) "lib-$cleaned".trimEnd('-') else cleaned
  }
}
