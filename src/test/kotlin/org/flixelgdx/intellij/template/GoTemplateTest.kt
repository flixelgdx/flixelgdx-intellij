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

class GoTemplateTest {

  private val values = TemplateValues(
    fields = mapOf("Title" to "My \"Game\"", "Kotlin" to false, "Width" to 640, "Empty" to ""),
    methods = mapOf("HasPlatform" to { name: String -> name == "desktop" }),
  )

  private fun render(text: String) = GoTemplate.render("test", text, values)

  @Test
  fun printsFieldsAndMethods() {
    assertEquals("My \"Game\" 640 true", render("{{.Title}} {{.Width}} {{.HasPlatform \"desktop\"}}"))
  }

  @Test
  fun ifElseAndNot() {
    assertEquals("java", render("{{if .Kotlin}}kotlin{{else}}java{{end}}"))
    assertEquals("yes", render("{{if not .Kotlin}}yes{{end}}"))
    assertEquals("", render("{{if .Empty}}set{{end}}"))
    assertEquals("web", render("{{if not .HasPlatform \"html5\"}}web{{end}}"))
  }

  @Test
  fun trimMarkersRemoveWhiteSpaceLikeGo() {
    assertEquals("a\nb", render("a\n{{- if .Kotlin}}\nk{{- end}}\nb"))
    assertEquals("x\ny", render("{{if not .Kotlin -}}\n  x\n{{end -}}\ny"))
  }

  @Test
  fun escapeFunctions() {
    assertEquals("My \\\"Game\\\"", render("{{kts .Title}}"))
    assertEquals("My &quot;Game&quot;", render("{{xml .Title}}"))
    assertEquals("My \\\"Game\\\"", render("{{toml .Title}}"))
  }

  @Test(expected = TemplateException::class)
  fun unknownFieldFails() {
    render("{{.Nope}}")
  }

  @Test(expected = TemplateException::class)
  fun unclosedIfFails() {
    render("{{if .Kotlin}}x")
  }

  @Test(expected = TemplateException::class)
  fun strayEndFails() {
    render("x{{end}}")
  }

  @Test(expected = TemplateException::class)
  fun rangeIsNotSupported() {
    render("{{range .Title}}{{end}}")
  }
}
