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
import org.flixelgdx.intellij.cli.flag
import org.flixelgdx.intellij.cli.objs
import org.flixelgdx.intellij.cli.str

/** How serious a doctor check is. */
enum class CheckStatus {
  OK,
  WARNING,
  ERROR;

  companion object {
    /** Maps the `status` text of a check; an unknown value counts as a warning so it is never hidden. */
    fun of(text: String?): CheckStatus = when (text) {
      "ok" -> OK
      "error" -> ERROR
      else -> WARNING
    }
  }
}

/**
 * One check of `flxgdx doctor`.
 *
 * @property id the stable id, such as `jdk` or `package.flixelgdx`.
 * @property category `environment` or `project`.
 * @property message what was found.
 * @property fix what to do about it; empty when the check is fine.
 * @property fixed whether `--fix` repaired the problem in this run.
 */
data class DoctorCheck(
  val id: String,
  val category: String,
  val status: CheckStatus,
  val message: String,
  val fix: String,
  val fixed: Boolean,
)

/**
 * The checks `flxgdx doctor` ran.
 *
 * @property checks every check, in the order `flxgdx` reported them.
 * @property inProject whether the folder was a FlixelGDX project, so the project checks ran.
 */
data class DoctorResult(val checks: List<DoctorCheck>, val inProject: Boolean) {

  /** How many checks have [status]. */
  fun count(status: CheckStatus): Int = checks.count { it.status == status }

  /** The number of checks that `--fix` repaired. */
  val fixedCount: Int get() = checks.count { it.fixed }

  /** The checks grouped by category, keeping the order of first appearance. */
  fun byCategory(): Map<String, List<DoctorCheck>> = checks.groupBy { it.category }

  companion object {
    /** Reads the `data` of the `result` event of `flxgdx doctor`. A `null` [data] gives an empty result. */
    fun parse(data: JsonObject?): DoctorResult {
      val checks = data.objs("checks").map { c ->
        DoctorCheck(
          id = c.str("id").orEmpty(),
          category = c.str("category").orEmpty(),
          status = CheckStatus.of(c.str("status")),
          message = c.str("message").orEmpty(),
          fix = c.str("fix").orEmpty(),
          fixed = c.flag("fixed"),
        )
      }
      return DoctorResult(checks, data.flag("project"))
    }
  }
}
