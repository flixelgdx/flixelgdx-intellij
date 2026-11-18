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
package org.flixelgdx.intellij.toml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogTextTest {

  @Test
  fun coordinatesAreParsedStrictly() {
    val expected = Coordinates("org.flixelgdx", "flixelgdx-ui", "0.7.2")
    assertEquals(expected, Coordinates.parse(" org.flixelgdx : flixelgdx-ui:0.7.2 "))
    assertNull(Coordinates.parse("org.flixelgdx:flixelgdx-ui"))
    assertNull(Coordinates.parse("a:b:c:d"))
    assertNull(Coordinates.parse("a b:c:1"))
    assertNull(Coordinates.parse("a::1"))
  }

  @Test
  fun aliasesFollowGradleRules() {
    assertNull(CatalogNames.aliasProblem("flixelgdx-ui"))
    assertNull(CatalogNames.aliasProblem("gdx.ai_2"))
    assertEquals("The alias must not be empty.", CatalogNames.aliasProblem(""))
    assertEquals("The alias must not end with '-'.", CatalogNames.aliasProblem("abc-"))
    assertEquals(true, CatalogNames.aliasProblem("Upper")?.startsWith("The alias must start"))
  }

  @Test
  fun aliasForArtifact() {
    assertEquals("flixelgdx-ui", CatalogNames.aliasFor("flixelgdx-ui"))
    assertEquals("my-lib", CatalogNames.aliasFor("My_Lib"))
    assertEquals("lib-2d", CatalogNames.aliasFor("2d"))
  }

  @Test
  fun settingsPluginVersion() {
    val text = "plugins {\n  id(\"org.flixelgdx.project\") version \"0.7.2\"\n}\n"
    assertEquals("0.7.2", SettingsPluginVersion.find(text))
    val edit = SettingsPluginVersion.edit(text, "0.8.0")!!
    assertEquals(text.replace("0.7.2", "0.8.0"), applyEdits(text, listOf(edit)))
    assertNull(SettingsPluginVersion.edit("plugins {}", "1"))
  }
}
