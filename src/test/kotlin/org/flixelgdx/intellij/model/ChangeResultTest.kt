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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangeResultTest {

  @Test
  fun readsAManualAdd() {
    val change = ChangeResult.parse(Fixtures.result("add-manual"))
    assertEquals("added", change.action)
    assertEquals("tiny-lib", change.pkg)
    assertEquals("1.2.3", change.tag)
    assertEquals(listOf("gradle/libs.versions.toml", "project.toml"), change.files)
    assertEquals("Added tiny-lib 1.2.3.", change.summary())
    assertFalse(change.unchanged)
  }

  @Test
  fun readsAManualPluginAdd() {
    val change = ChangeResult.parse(Fixtures.result("add-plugin-manual"))
    assertEquals("added", change.action)
    assertEquals("plugin", Fixtures.result("add-plugin-manual").get("kind").asString)
  }

  @Test
  fun readsAManualRemove() {
    val change = ChangeResult.parse(Fixtures.result("remove-manual"))
    assertEquals("removed", change.action)
    assertEquals("Removed tiny-lib 1.2.3.", change.summary())
  }

  @Test
  fun readsPlatformChanges() {
    val added = ChangeResult.parse(Fixtures.result("add-platform"))
    assertEquals("android", added.platform)
    assertEquals("Added the android platform.", added.summary())
    val removed = ChangeResult.parse(Fixtures.result("remove-platform"))
    assertEquals("Removed the android platform.", removed.summary())
  }

  @Test
  fun readsNotesAndSuggestions() {
    val data = JsonParser.parseString(
      """
      {"action":"added","package":"ui","tag":"v1","notes":[{"package":"ui","text":"Read me."},{"text":" "}],
       "suggestions":[{"repo":"o/r","package":"x","command":"flxgdx add library --repo o/r"}],"future":true}
      """.trimIndent(),
    ).asJsonObject
    val change = ChangeResult.parse(data)
    assertEquals(listOf("ui: Read me."), change.notes)
    assertEquals(listOf("flxgdx add library --repo o/r"), change.suggestions)
  }

  @Test
  fun unchangedMeansNothingToRefresh() {
    val data = JsonParser.parseString("""{"action":"unchanged","package":"ui","tag":"v1"}""").asJsonObject
    val change = ChangeResult.parse(data)
    assertTrue(change.unchanged)
    assertEquals("Nothing changed: ui v1 is already installed.", change.summary())
  }

  @Test
  fun anEmptyResultIsHarmless() {
    val change = ChangeResult.parse(null)
    assertEquals("Done.", change.summary())
    assertTrue(change.notes.isEmpty())
  }
}
