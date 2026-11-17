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

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Test

class NewProjectResultTest {

  @Test
  fun readsNotesAndInstalled() {
    val data = JsonParser.parseString(
      """
      {"dir":"/p/g","installed":[{"package":"a"},{"package":"b"}],
       "notes":[{"package":"b","module":"core","text":"Read me."},{"text":"No package."},{"text":" "}],
       "future":1}
      """.trimIndent(),
    ).asJsonObject
    val parsed = NewProjectResult.parse(data)
    assertEquals("/p/g", parsed.dir)
    assertEquals(2, parsed.installed)
    assertEquals(listOf("b: Read me.", "No package."), parsed.notes)
    assertEquals(NewProjectResult(null, 0, emptyList()), NewProjectResult.parse(null))
  }
}
