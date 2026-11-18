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

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.toml.lang.psi.TomlFile

class VersionCatalogTest : BasePlatformTestCase() {

  private fun file(text: String): TomlFile = myFixture.configureByText("libs.versions.toml", text) as TomlFile

  private fun edit(text: String, plan: (VersionCatalog) -> List<TextEdit>): String {
    val file = file(text)
    return applyEdits(file.text, plan(VersionCatalog(file)))
  }

  fun testReadsLibrariesBundlesAndVersions() {
    val catalog = VersionCatalog(file(GAME))
    val aliases = catalog.libraries().map { it.alias }
    assertEquals(listOf("flixelgdx-core", "flixelgdx-desktop", "kotlin-gradle-plugin"), aliases)
    val core = catalog.libraries().first()
    assertEquals("org.flixelgdx:flixelgdx-core", core.module)
    assertEquals("flixelgdx", core.versionRef)
    assertTrue(core.framework)
    assertEquals(listOf("core", "desktop", "android", "buildsrc"), catalog.bundles())
    assertEquals(listOf("flixelgdx-core"), catalog.bundle("core"))
    assertEquals("0.7.2", catalog.version("flixelgdx"))
    assertNull(catalog.version("nope"))
  }

  fun testAddLibraryKeepsCommentsAndAppendsToTheBundle() {
    val ui = Coordinates.parse("org.flixelgdx:flixelgdx-ui:0.7.2")!!
    val result = edit(GAME) { it.addLibrary("flixelgdx-ui", ui, "core") }
    assertEquals(
      GAME
        .replace(
          "kotlin-gradle-plugin = { module = \"org.jetbrains.kotlin:kotlin-gradle-plugin\", version.ref = \"kotlin\" }",
          "kotlin-gradle-plugin = { module = \"org.jetbrains.kotlin:kotlin-gradle-plugin\", " +
            "version.ref = \"kotlin\" }\n" +
            "flixelgdx-ui = { module = \"org.flixelgdx:flixelgdx-ui\", version = \"0.7.2\" }",
        )
        .replace("core = [\"flixelgdx-core\"]", "core = [\"flixelgdx-core\", \"flixelgdx-ui\"]"),
      result,
    )
  }

  fun testAddLibraryToAnEmptyBundle() {
    val ai = Coordinates.parse("com.badlogicgames.gdx:gdx-ai:1.8.2")!!
    val result = edit(GAME) { it.addLibrary("gdx-ai", ai, "android") }
    assertTrue(result.contains("android = [\"gdx-ai\"]"))
  }

  fun testAddLibraryCreatesAMissingBundle() {
    val result = edit(GAME) { it.addLibrary("lib", Coordinates.parse("a:b:1")!!, "coreProcessors") }
    assertTrue(result.contains("buildsrc = [\"kotlin-gradle-plugin\"]\ncoreProcessors = [\"lib\"]"))
  }

  fun testAddLibraryRefusesATakenAlias() {
    try {
      edit(GAME) { it.addLibrary("flixelgdx-core", Coordinates.parse("a:b:1")!!, "core") }
      fail("expected an IllegalArgumentException")
    } catch (expected: IllegalArgumentException) {
      // The alias is already in the catalog.
    }
  }

  fun testRemoveLibraryRemovesEntryBundleItemAndUnusedVersion() {
    val result = edit(GAME) { it.removeLibrary("kotlin-gradle-plugin") }
    assertFalse(result.contains("kotlin-gradle-plugin"))
    assertFalse(result.contains("kotlin = \"2.3.21\""))
    assertTrue(result.contains("buildsrc = []"))
    assertTrue(result.contains("# The framework version."))
  }

  fun testRemoveFromTheMiddleOfABundle() {
    val text = GAME.replace("core = [\"flixelgdx-core\"]", "core = [\"a\", \"flixelgdx-core\", \"b\"]")
    val result = edit(text) { it.removeLibrary("flixelgdx-core") }
    assertTrue(result.contains("core = [\"a\", \"b\"]"))
    assertTrue(result.contains("flixelgdx = \"0.7.2\""))
  }

  fun testSetVersion() {
    val result = edit(GAME) { it.setVersion("flixelgdx", "0.8.0") }
    assertEquals(GAME.replace("flixelgdx = \"0.7.2\"", "flixelgdx = \"0.8.0\""), result)
    assertEquals(GAME, edit(GAME) { it.setVersion("nope", "1") })
  }

  private companion object {
    val GAME = """
      # A comment at the top.
      [versions]
      # The framework version.
      flixelgdx = "0.7.2"
      kotlin = "2.3.21"

      [libraries]
      flixelgdx-core = { module = "org.flixelgdx:flixelgdx-core", version.ref = "flixelgdx" } # kept
      flixelgdx-desktop = { group = "org.flixelgdx", name = "flixelgdx-desktop", version.ref = "flixelgdx" }
      kotlin-gradle-plugin = { module = "org.jetbrains.kotlin:kotlin-gradle-plugin", version.ref = "kotlin" }

      [bundles]
      core = ["flixelgdx-core"]
      desktop = ["flixelgdx-desktop"]
      android = []
      buildsrc = ["kotlin-gradle-plugin"]
    """.trimIndent() + "\n"
  }
}
