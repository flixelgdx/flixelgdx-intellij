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
import org.flixelgdx.intellij.cli.int
import org.flixelgdx.intellij.cli.str

/**
 * An installed handbook, found by reading the `.flxgdx-handbook.toml` marker that `flxgdx install handbook` writes.
 *
 * @property name the name of the folder inside `handbooks/`.
 * @property repo the repository the docs came from; `flxgdx uninstall handbook --repo` takes this value.
 * @property tag the release the docs were taken from, or empty.
 */
data class Handbook(val name: String, val repo: String, val tag: String) {

  /** A readable name for lists, such as `flixelgdx (owner/repo, v1.0.0)`. */
  val label: String
    get() = if (tag.isEmpty()) "$name ($repo)" else "$name ($repo, $tag)"

  companion object {
    /** The name of the marker file inside every handbook folder. */
    const val MARKER = ".flxgdx-handbook.toml"

    /** The folder that holds the handbooks, relative to the project folder. */
    const val FOLDER = "handbooks"

    /**
     * Reads the marker text of the handbook folder called [name].
     *
     * The marker is a flat list of `key = "value"` lines, so a tiny line reader is enough and no TOML library is
     * needed. Returns `null` when there is no `repo` key, because `flxgdx` could not uninstall such a folder.
     */
    fun fromMarker(name: String, marker: String): Handbook? {
      var repo = ""
      var tag = ""
      for (raw in marker.lineSequence()) {
        val line = raw.trim()
        if (line.startsWith("#")) continue
        val eq = line.indexOf('=')
        if (eq <= 0) continue
        val value = line.substring(eq + 1).trim().removeSurrounding("\"")
        when (line.substring(0, eq).trim()) {
          "repo" -> repo = value
          "tag" -> tag = value
        }
      }
      return if (repo.isEmpty()) null else Handbook(name, repo, tag)
    }
  }
}

/**
 * What `flxgdx install handbook` reports.
 *
 * @property action `installed`, `updated`, `reinstalled` or `unchanged`.
 * @property name the folder name inside `handbooks/`.
 * @property tag the release the docs were taken from.
 * @property files how many files were installed.
 */
data class HandbookResult(val action: String, val name: String, val tag: String, val files: Int) {

  /** A short sentence for a notification. */
  fun summary(): String = when (action) {
    "unchanged" -> "The $name handbook is already installed at $tag."
    "updated" -> "Updated the $name handbook to $tag."
    else -> "Installed the $name handbook ($tag, $files files)."
  }

  companion object {
    /** Reads the `data` of the `result` event of `flxgdx install handbook`. */
    fun parse(data: JsonObject?): HandbookResult = HandbookResult(
      action = data.str("action").orEmpty(),
      name = data.str("name").orEmpty(),
      tag = data.str("tag").orEmpty(),
      files = data.int("files") ?: 0,
    )
  }
}
