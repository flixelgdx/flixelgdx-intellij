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
package org.flixelgdx.intellij.template

import org.junit.Assert.assertEquals
import org.junit.Test

class TextTemplateTest {

  private val values = mapOf("title" to "My \"Game\"", "kotlin" to false, "width" to 640, "empty" to "", "desktop" to true)

  private fun render(text: String) = TextTemplate.render("test", text, values)

  @Test
  fun printsValues() {
    assertEquals("My \"Game\" 640 true", render("{{title}} {{ width }} {{desktop}}"))
  }

  @Test
  fun ifElseAndNot() {
    assertEquals("java", render("{{if kotlin}}kotlin{{else}}java{{end}}"))
    assertEquals("yes", render("{{if not kotlin}}yes{{end}}"))
    assertEquals("", render("{{if empty}}set{{end}}"))
    assertEquals("", render("{{if not desktop}}web{{end}}"))
  }

  @Test
  fun trimMarkersRemoveWhiteSpace() {
    assertEquals("a\nb", render("a\n{{- if kotlin}}\nk{{- end}}\nb"))
    assertEquals("x\ny", render("{{if not kotlin -}}\n  x\n{{end -}}\ny"))
  }

  @Test
  fun filtersEscape() {
    assertEquals("My \\\"Game\\\"", render("{{title | kotlin}}"))
    assertEquals("My &quot;Game&quot;", render("{{title | xml}}"))
    assertEquals("My \\\"Game\\\"", render("{{title|toml}}"))
  }

  @Test(expected = TemplateException::class)
  fun unknownValueFails() {
    render("{{nope}}")
  }

  @Test(expected = TemplateException::class)
  fun unknownFilterFails() {
    render("{{title | json}}")
  }

  @Test(expected = TemplateException::class)
  fun unclosedIfFails() {
    render("{{if kotlin}}x")
  }

  @Test(expected = TemplateException::class)
  fun strayEndFails() {
    render("x{{end}}")
  }

  @Test(expected = TemplateException::class)
  fun unknownSyntaxFails() {
    render("{{for title}}{{end}}")
  }
}
