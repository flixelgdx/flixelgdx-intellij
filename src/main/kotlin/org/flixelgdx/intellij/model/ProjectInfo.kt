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
import org.flixelgdx.intellij.cli.obj
import org.flixelgdx.intellij.cli.objs
import org.flixelgdx.intellij.cli.str
import org.flixelgdx.intellij.cli.strs

/**
 * One package that is installed in a FlixelGDX project, as `flxgdx info` reports it.
 *
 * @property repo the repository it came from, or an empty string for a package that was added by hand.
 * @property pkg the package name (for a manual entry, the artifact name).
 * @property kind `library` or `plugin`.
 * @property tag the installed release tag (for a manual entry, the version that was given).
 * @property framework whether this package holds the framework itself, which can never be removed.
 * @property manual whether it was added by hand.
 * @property alias the catalog alias that removes a manual entry; empty for entries from a repository.
 */
data class InstalledPackage(
  val repo: String,
  val pkg: String,
  val kind: String,
  val tag: String,
  val framework: Boolean,
  val manual: Boolean,
  val alias: String,
) {
  /** Whether the package is a library (`remove library` removes it). Everything else is treated as a plugin. */
  val isLibrary: Boolean get() = kind == KIND_LIBRARY

  /** A readable name for lists, such as `flixelgdx-ui (owner/repo) v1.0.0`. */
  val label: String
    get() {
      val origin = if (manual) "added by hand" else repo
      val release = if (tag.isEmpty()) "" else " $tag"
      return if (origin.isEmpty()) "$pkg$release" else "$pkg ($origin)$release"
    }

  companion object {
    const val KIND_LIBRARY = "library"
    const val KIND_PLUGIN = "plugin"
  }
}

/**
 * What `flxgdx info` says about the project in the working folder.
 *
 * @property inProject whether the folder is a FlixelGDX project at all.
 * @property platforms the enabled platforms, without `core` (for example `desktop` and `html5`).
 * @property packages every installed package: first those from repositories, then the ones added by hand.
 */
data class ProjectInfo(
  val inProject: Boolean,
  val platforms: List<String>,
  val packages: List<InstalledPackage>,
) {

  /** The platforms of FlixelGDX that are not enabled yet, in the usual order. */
  val missingPlatforms: List<String> get() = ALL_PLATFORMS.filter { it !in platforms }

  /** The modules a dependency can be added to: `core` and every enabled platform. */
  val modules: List<String> get() = listOf("core") + platforms

  companion object {
    /** The platforms `flxgdx` knows. */
    val ALL_PLATFORMS = listOf("desktop", "html5", "android")

    /** Reads the `data` of the `result` event of `flxgdx info`. A `null` [data] gives a project-less answer. */
    fun parse(data: JsonObject?): ProjectInfo {
      if (data == null || !data.flag("in_project")) return ProjectInfo(false, emptyList(), emptyList())
      val project = data.obj("project")
      val packages = ArrayList<InstalledPackage>()
      for (entry in project.objs("installed")) packages += pkg(entry, manual = false)
      for (entry in project.objs("manual")) packages += pkg(entry, manual = true)
      return ProjectInfo(true, project.strs("platforms"), packages)
    }

    private fun pkg(entry: JsonObject, manual: Boolean): InstalledPackage {
      val alias = if (manual) {
        (entry.objs("libraries") + entry.objs("plugins")).firstNotNullOfOrNull { it.str("alias") }.orEmpty()
      } else {
        ""
      }
      return InstalledPackage(
        repo = entry.str("repo").orEmpty(),
        pkg = entry.str("package").orEmpty(),
        kind = entry.str("kind") ?: InstalledPackage.KIND_LIBRARY,
        tag = entry.str("tag").orEmpty(),
        framework = entry.flag("framework"),
        manual = manual,
        alias = alias,
      )
    }
  }
}
