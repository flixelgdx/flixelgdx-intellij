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

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HandbookTest {

  @Test
  fun readsTheMarkerFile() {
    val marker = """
      # written by flxgdx
      repo = "owner/docs"
      tag = "v1.2.3"
      source = "path"
      commit = "abc"
    """.trimIndent()
    val book = Handbook.fromMarker("docs", marker)
    assertEquals(Handbook("docs", "owner/docs", "v1.2.3"), book)
    assertEquals("docs (owner/docs, v1.2.3)", book?.label)
  }

  @Test
  fun aMarkerWithoutARepoIsSkipped() {
    assertNull(Handbook.fromMarker("docs", "tag = \"v1\""))
    assertNull(Handbook.fromMarker("docs", ""))
  }

  @Test
  fun summarizesAnInstall() {
    val data = JsonParser.parseString(
      """{"action":"installed","repo":"o/r","name":"docs","tag":"v1","files":23,"extra":1}""",
    ).asJsonObject
    val result = HandbookResult.parse(data)
    assertEquals(23, result.files)
    assertEquals("Installed the docs handbook (v1, 23 files).", result.summary())
    assertEquals("The docs handbook is already installed at v1.", result.copy(action = "unchanged").summary())
    assertEquals("Updated the docs handbook to v1.", result.copy(action = "updated").summary())
  }
}
