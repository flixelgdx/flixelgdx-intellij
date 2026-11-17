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
 */
package org.flixelgdx.intellij.wizard

import com.google.gson.JsonObject
import org.flixelgdx.intellij.cli.CliNotes
import org.flixelgdx.intellij.cli.stringAt

/**
 * What `flxgdx new` reports about the project it created.
 *
 * It reads only the keys it needs from the `result` data and ignores the rest, so newer versions of `flxgdx` keep
 * working.
 *
 * @property dir the absolute path of the new project, or `null` when it was not reported.
 * @property installed how many packages were installed (the framework, libraries, plugins and suggestions).
 * @property notes the notes that the installed packages want shown to the person.
 */
data class NewProjectResult(val dir: String?, val installed: Int, val notes: List<String>) {

  companion object {
    /** Reads the `data` of the `result` event of `flxgdx new`. A `null` [data] gives an empty result. */
    fun parse(data: JsonObject?): NewProjectResult {
      if (data == null) return NewProjectResult(null, 0, emptyList())
      val installed = data.get("installed")?.takeIf { it.isJsonArray }?.asJsonArray?.size() ?: 0
      return NewProjectResult(data.stringAt("dir"), installed, CliNotes.parse(data))
    }
  }
}
