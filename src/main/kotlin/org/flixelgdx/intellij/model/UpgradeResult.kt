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
import org.flixelgdx.intellij.cli.flag
import org.flixelgdx.intellij.cli.obj
import org.flixelgdx.intellij.cli.objs
import org.flixelgdx.intellij.cli.str
import org.flixelgdx.intellij.cli.strs

/**
 * One package that has a newer release.
 *
 * @property pkg the package name.
 * @property repo the repository it comes from.
 * @property from the installed tag.
 * @property to the tag it would move to.
 * @property framework whether this is the package that holds the framework.
 * @property removed what the new release drops from the project (aliases and plugin ids).
 * @property files the project files the upgrade would change.
 */
data class PlannedUpdate(
  val pkg: String,
  val repo: String,
  val from: String,
  val to: String,
  val framework: Boolean,
  val removed: List<String>,
  val files: List<String>,
) {
  /** A one-line description such as `flixelgdx: v1.0.0 to v1.1.0 (framework)`. */
  fun line(): String = "$pkg: $from to $to" + if (framework) " (framework)" else ""
}

/**
 * What `flxgdx upgrade` reports.
 *
 * @property action `none`, `pending`, `declined`, `dry_run` or `upgraded`.
 * @property updates the packages that have a newer release.
 * @property manual packages added by hand, which are never upgraded.
 * @property handbooks the names of installed handbooks that are older than their package.
 * @property notes the notes of the new releases, one line each.
 * @property warnings problems that did not stop the command.
 */
data class UpgradeResult(
  val action: String,
  val updates: List<PlannedUpdate>,
  val manual: List<String>,
  val handbooks: List<String>,
  val notes: List<String>,
  val warnings: List<String>,
) {
  /** Whether there is nothing to upgrade. */
  val upToDate: Boolean get() = updates.isEmpty()

  /**
   * The plan as plain lines for a dialog: one line per update (with what it drops indented below), then the packages
   * that are skipped and the handbooks that are behind.
   */
  fun plan(): List<String> {
    val lines = ArrayList<String>()
    for (u in updates) {
      lines += u.line()
      if (u.removed.isNotEmpty()) lines += "    drops: " + u.removed.joinToString(", ")
    }
    if (manual.isNotEmpty()) lines += "Skipped (added by hand): " + manual.joinToString(", ")
    if (handbooks.isNotEmpty()) lines += "Handbooks that are behind: " + handbooks.joinToString(", ")
    return lines
  }

  companion object {
    /** Reads the `data` of the `result` event of `flxgdx upgrade`. A `null` [data] gives an up-to-date result. */
    fun parse(data: JsonObject?): UpgradeResult {
      val updates = data.objs("updates").map { u ->
        val changes = u.obj("changes")
        PlannedUpdate(
          pkg = u.str("package").orEmpty(),
          repo = u.str("repo").orEmpty(),
          from = u.str("from").orEmpty(),
          to = u.str("to").orEmpty(),
          framework = u.flag("framework"),
          removed = changes.strs("removed"),
          files = changes.strs("files"),
        )
      }
      return UpgradeResult(
        action = data.str("action").orEmpty(),
        updates = updates,
        manual = data.strs("manual"),
        handbooks = data.objs("handbooks").mapNotNull { it.str("name") },
        notes = data.objs("updates").flatMap { CliNotes.parse(it.obj("changes")) },
        warnings = data.strs("warnings"),
      )
    }
  }
}
