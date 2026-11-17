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
package org.flixelgdx.intellij.model

import com.google.gson.JsonObject
import org.flixelgdx.intellij.cli.CliNotes
import org.flixelgdx.intellij.cli.objs
import org.flixelgdx.intellij.cli.str
import org.flixelgdx.intellij.cli.strs

/**
 * What `flxgdx add` and `flxgdx remove` report about a change to the project.
 *
 * @property action `added`, `updated`, `unchanged` or `removed`; empty when it was not reported.
 * @property pkg the package that changed (empty for platform changes).
 * @property platform the platform that was added or removed (empty for package changes).
 * @property tag the release tag or version, or empty.
 * @property notes the notes of the installed packages, one line each.
 * @property suggestions the packages the new one suggests, as the exact commands that would install them.
 * @property files the project files that were changed, relative to the project folder.
 */
data class ChangeResult(
  val action: String,
  val pkg: String,
  val platform: String,
  val tag: String,
  val notes: List<String>,
  val suggestions: List<String>,
  val files: List<String>,
) {
  /** Whether the project was left as it was (the package was already installed at this release). */
  val unchanged: Boolean get() = action == "unchanged"

  /** A short sentence that says what happened, for a notification. */
  fun summary(): String {
    val what = when {
      platform.isNotEmpty() -> "the $platform platform"
      tag.isNotEmpty() -> "$pkg $tag"
      else -> pkg.ifEmpty { "the package" }
    }
    return when (action) {
      "added" -> "Added $what."
      "updated" -> "Updated $what."
      "removed" -> "Removed $what."
      "unchanged" -> "Nothing changed: $what is already installed."
      else -> "Done."
    }
  }

  companion object {
    /** Reads the `data` of the `result` event of an add or remove command. A `null` [data] gives an empty result. */
    fun parse(data: JsonObject?): ChangeResult = ChangeResult(
      action = data.str("action").orEmpty(),
      pkg = data.str("package").orEmpty(),
      platform = data.str("platform").orEmpty(),
      tag = data.str("tag").orEmpty(),
      notes = CliNotes.parse(data),
      suggestions = data.objs("suggestions").mapNotNull { it.str("command") },
      files = data.strs("files"),
    )
  }
}
