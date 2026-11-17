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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProjectTomlTest {

  @Test
  fun readsASingleLineList() {
    val text = "name = \"x\"\nplatforms = [\"desktop\", \"html5\"]\n"
    assertEquals(listOf("desktop", "html5"), ProjectToml.platforms(text))
  }

  @Test
  fun readsAMultiLineListWithCommentsAndSingleQuotes() {
    val text = "platforms = [\n  \"desktop\", # the usual one\n  'android',\n]\n"
    assertEquals(listOf("desktop", "android"), ProjectToml.platforms(text))
  }

  @Test
  fun acceptsAnEmptyListAndDropsDuplicates() {
    assertEquals(emptyList<String>(), ProjectToml.platforms("platforms = []"))
    assertEquals(listOf("desktop"), ProjectToml.platforms("platforms = [\"desktop\", \"desktop\"]"))
  }

  @Test
  fun ignoresKeysInsideTables() {
    assertNull(ProjectToml.platforms("[app]\nplatforms = [\"desktop\"]\n"))
    assertEquals(listOf("html5"), ProjectToml.platforms("platforms = [\"html5\"]\n[app]\nplatforms = [\"desktop\"]\n"))
  }

  @Test
  fun givesUpWhenTheKeyIsMissingOrRepeated() {
    assertNull(ProjectToml.platforms(""))
    assertNull(ProjectToml.platforms("platforms = [\"desktop\"]\nplatforms = [\"html5\"]\n"))
  }

  @Test
  fun givesUpOnAnythingOdd() {
    assertNull(ProjectToml.platforms("platforms = \"desktop\""))
    assertNull(ProjectToml.platforms("platforms = [\"desktop\""))
    assertNull(ProjectToml.platforms("platforms = [desktop]"))
    assertNull(ProjectToml.platforms("platforms = [\"desk\\top\"]"))
    assertNull(ProjectToml.platforms("platforms = [[\"desktop\"]]"))
  }

  @Test
  fun doesNotCutAHashInsideQuotes() {
    assertEquals(listOf("a#b"), ProjectToml.platforms("platforms = [\"a#b\"]"))
  }
}
