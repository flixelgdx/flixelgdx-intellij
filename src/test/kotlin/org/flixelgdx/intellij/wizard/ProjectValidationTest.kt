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
package org.flixelgdx.intellij.wizard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks [ProjectValidation] and [PackageNames] against the same table of examples that
 * `internal/project/project_test.go` (TestValidators) and `internal/templates/templates_test.go` (TestPackageName)
 * use in the flxgdx repository. When a row changes there, change it here too.
 */
class ProjectValidationTest {

  private class Case(val name: String, val want: String, val run: () -> FieldError?)

  private val long = "x".repeat(300)

  private val cases = listOf(
    Case("title ok", "") { ProjectValidation.title("My Game 2") },
    Case("title long ok", "") { ProjectValidation.title(long) },
    Case("title blank", "must not be blank") { ProjectValidation.title("   ") },
    Case("title empty", "must not be blank") { ProjectValidation.title("") },
    Case("title non ascii", "U+00E9 at position 4") { ProjectValidation.title("Café") },
    Case("title control", "U+0009 at position 2") { ProjectValidation.title("a\tb") },
    Case("title slash", "character '/' at position 2") { ProjectValidation.title("a/b") },
    Case("title star", "character '*'") { ProjectValidation.title("a*") },
    Case("title leading dot", "must not start with the character '.'") { ProjectValidation.title(".a") },
    Case("title trailing space", "must not end with the character ' '") { ProjectValidation.title("a ") },
    Case("title reserved", "reserved name on Windows") { ProjectValidation.title("com1") },
    Case("company bad", "app.company") { ProjectValidation.company("a|b") },
    Case("version ok", "") { ProjectValidation.version("1.0.0-rc.1+build5") },
    Case("version empty", "must not be empty") { ProjectValidation.version("") },
    Case("version letter first", "must start with a digit") { ProjectValidation.version("v1") },
    Case("version space", "character ' ' at position 2") { ProjectValidation.version("1 0") },
    Case("package ok", "") { ProjectValidation.packageName("com.example.my_game2") },
    Case("package single", "") { ProjectValidation.packageName("game") },
    Case("package empty", "empty part") { ProjectValidation.packageName("") },
    Case("package doubled dot", "empty part") { ProjectValidation.packageName("a..b") },
    Case("package trailing dot", "empty part") { ProjectValidation.packageName("a.") },
    Case("package upper", "character 'E' in 'Example'") { ProjectValidation.packageName("com.Example") },
    Case("package digit first", "character '1' in '1b'") { ProjectValidation.packageName("a.1b") },
    Case("package hyphen", "character '-'") { ProjectValidation.packageName("my-game") },
    Case("package keyword", "Java keyword") { ProjectValidation.packageName("com.class") },
    Case("package underscore", "Java keyword") { ProjectValidation.packageName("a._") },
    Case("language ok", "") { ProjectValidation.language("kotlin") },
    Case("language bad", "not \"Java\"") { ProjectValidation.language("Java") },
    Case("size ok", "") { ProjectValidation.width(1) },
    Case("size negative", "not -3") { ProjectValidation.height(-3) },
    Case("size max", "out of range") { ProjectValidation.width(1L shl 31) },
    Case("platform ok", "") { ProjectValidation.platform("android") },
    Case("platform ios", "not supported yet") { ProjectValidation.platform("ios") },
    Case("platform bad", "\"Desktop\" is not a platform") { ProjectValidation.platform("Desktop") },
  )

  @Test
  fun everyRowOfTheSharedTableAgrees() {
    for (case in cases) {
      val error = case.run()
      if (case.want.isEmpty()) {
        assertNull("${case.name}: unexpected $error", error)
      } else {
        assertTrue("${case.name}: got $error", error != null)
        assertTrue("${case.name}: '${error.toString()}' lacks '${case.want}'", error.toString().contains(case.want))
      }
    }
  }

  @Test
  fun messagesUseTheFlxgdxWording() {
    assertEquals(
      "Invalid app.title in project.toml: it must not be blank.",
      ProjectValidation.title("")?.toString(),
    )
    assertEquals(
      "Invalid window.width in project.toml: it must be greater than 0, not 0.",
      ProjectValidation.width(0)?.toString(),
    )
    assertEquals(
      "Invalid window.width in project.toml: 2147483648 is out of range.",
      ProjectValidation.width(2147483648L)?.toString(),
    )
  }

  @Test
  fun sentenceReplacesTheLeadingIt() {
    assertEquals("Title must not be blank.", ProjectValidation.title("")?.sentence("Title"))
    assertEquals("Width: 5000000000 is out of range.", ProjectValidation.width(5_000_000_000L)?.sentence("Width"))
  }

  @Test
  fun positionsCountCodePointsNotUtf16Units() {
    val error = ProjectValidation.title("a😀")
    assertTrue(error.toString(), error.toString().contains("U+1F600 at position 2"))
  }

  @Test
  fun packageDerivationAgreesWithTheGoTable() {
    val table = listOf(
      Triple("Test Studio", "Java Game", "com.teststudio.javagame"),
      Triple("My Studio", "My Game", "com.mystudio.mygame"),
      Triple("Acme", "2048", "com.acme._2048"),
      Triple("new", "Class", "com.new_.class_"),
      Triple("!!!", "Game", ""),
      Triple("Acme", "", ""),
    )
    for ((company, title, want) in table) {
      val got = PackageNames.derive(company, title)
      assertEquals("derive($company, $title)", want, got)
      if (got.isNotEmpty()) assertNull("$got must be valid", ProjectValidation.packageName(got))
    }
  }
}
