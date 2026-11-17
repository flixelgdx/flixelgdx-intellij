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
import org.flixelgdx.intellij.cli.objs
import org.flixelgdx.intellij.cli.str

/**
 * The packages a `flxgdx.yml` manifest declares, as `flxgdx manifest check` reports them.
 *
 * @property packages the package names, in manifest order, with their kinds (`library` or `plugin`).
 */
data class ManifestInfo(val packages: List<ManifestPackage>) {

  /** The names of the packages of [kind], for filling a combo box. */
  fun names(kind: String): List<String> = packages.filter { it.kind == kind }.map { it.name }

  companion object {
    /** Reads the `data` of the `result` event of `flxgdx manifest check`. A `null` [data] gives an empty list. */
    fun parse(data: JsonObject?): ManifestInfo =
      ManifestInfo(
        data.objs("packages").mapNotNull { p ->
          val name = p.str("name")
          if (name.isNullOrEmpty()) null else ManifestPackage(name, p.str("kind").orEmpty())
        },
      )
  }
}

/** One package of a manifest: its [name] and its [kind]. */
data class ManifestPackage(val name: String, val kind: String)
