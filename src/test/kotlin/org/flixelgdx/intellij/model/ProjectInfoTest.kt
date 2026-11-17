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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectInfoTest {

  @Test
  fun readsAProjectFromARealResult() {
    val info = ProjectInfo.parse(Fixtures.result("info"))
    assertTrue(info.inProject)
    assertEquals(listOf("desktop", "html5"), info.platforms)
    assertEquals(listOf("android"), info.missingPlatforms)
    assertEquals(listOf("core", "desktop", "html5"), info.modules)
    val framework = info.packages.single()
    assertEquals("flixelgdx", framework.pkg)
    assertEquals("/path/to/framework", framework.repo)
    assertEquals("v0.7.2", framework.tag)
    assertTrue(framework.framework)
    assertFalse(framework.manual)
  }

  @Test
  fun readsManualEntriesWithTheirAlias() {
    val info = ProjectInfo.parse(Fixtures.result("info-manual"))
    assertEquals(2, info.packages.size)
    val manual = info.packages.last()
    assertTrue(manual.manual)
    assertEquals("tiny-lib", manual.alias)
    assertEquals("1.2.3", manual.tag)
    assertEquals("", manual.repo)
    assertTrue(manual.isLibrary)
    assertEquals("tiny-lib (added by hand) 1.2.3", manual.label)
  }

  @Test
  fun outsideAProjectThereIsNothing() {
    val data = JsonParser.parseString("""{"in_project":false,"project":null}""").asJsonObject
    assertFalse(ProjectInfo.parse(data).inProject)
    assertFalse(ProjectInfo.parse(null).inProject)
  }

  @Test
  fun ignoresUnknownKeysAndMissingLists() {
    val data = JsonParser.parseString("""{"in_project":true,"project":{"future":1},"extra":[]}""").asJsonObject
    val info = ProjectInfo.parse(data)
    assertTrue(info.inProject)
    assertTrue(info.platforms.isEmpty())
    assertTrue(info.packages.isEmpty())
    assertEquals(ProjectInfo.ALL_PLATFORMS, info.missingPlatforms)
    assertNull(info.packages.firstOrNull())
  }

  @Test
  fun labelShowsTheRepositoryAndTag() {
    val pkg = InstalledPackage("owner/repo", "ui", "library", "v1.0.0", framework = false, manual = false, alias = "")
    assertEquals("ui (owner/repo) v1.0.0", pkg.label)
  }
}
