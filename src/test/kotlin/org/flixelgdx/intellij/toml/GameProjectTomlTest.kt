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

import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.flixelgdx.intellij.template.GameLanguage
import org.flixelgdx.intellij.template.GameTemplateData
import org.flixelgdx.intellij.template.GameTemplates
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlFileType

class GameProjectTomlTest : BasePlatformTestCase() {

  private fun toml(name: String, text: String): TomlFile =
    PsiFileFactory.getInstance(project).createFileFromText(name, TomlFileType, text) as TomlFile

  private fun render(language: GameLanguage, vararg platforms: String): Map<String, String> {
    val data = GameTemplateData.of(
      "Java Game", "Test Studio", "0.1.0", "com.teststudio.javagame", language, platforms.toList(),
      "1.2.3", "0.4.0", "my-game", 640, 360,
    )
    return GameTemplates.render(data, GameTemplates.DEFAULT).associate { it.path to it.text() }
  }

  fun testReadsSettings() {
    val text = render(GameLanguage.KOTLIN, "desktop", "android").getValue(GameProjectToml.PATH)
    val settings = GameProjectToml(toml("project.toml", text)).settings()
    assertEquals("Java Game", settings.title)
    assertEquals("Test Studio", settings.company)
    assertEquals("0.1.0", settings.version)
    assertEquals("com.teststudio.javagame", settings.pkg)
    assertEquals(GameLanguage.KOTLIN, settings.language)
    assertEquals(listOf("desktop", "android"), settings.platforms)
    assertEquals(640, settings.width)
    assertEquals(360, settings.height)
  }

  fun testAddAndRemovePlatformKeepTheRestOfTheFile() {
    val text = render(GameLanguage.JAVA, "desktop").getValue(GameProjectToml.PATH)
    val file = toml("project.toml", text)
    val added = TomlEdits.applyTo(text, GameProjectToml(file).addPlatform("html5"))
    assertEquals(text.replace("platforms = [\"desktop\"]", "platforms = [\"desktop\", \"html5\"]"), added)
    assertEquals(emptyList<TextEdit>(), GameProjectToml(file).addPlatform("desktop"))
    val removed = TomlEdits.applyTo(added, GameProjectToml(toml("project.toml", added)).removePlatform("desktop"))
    assertEquals(text.replace("platforms = [\"desktop\"]", "platforms = [\"html5\"]"), removed)
  }

  fun testMissingPlatformsKeyIsAddedAtTheTop() {
    val text = "[app]\ntitle = \"x\"\n"
    val edits = GameProjectToml(toml("project.toml", text)).addPlatform("android")
    assertEquals("platforms = [\"android\"]\n$text", TomlEdits.applyTo(text, edits))
  }

  /** Merging the rendered catalog of a bigger platform set into a smaller one gives the bigger one's entries. */
  fun testCatalogMergeAddsWhatANewPlatformNeeds() {
    for (language in GameLanguage.entries) {
      val before = render(language, "desktop").getValue(VersionCatalog.PATH)
      val after = render(language, "desktop", "html5", "android").getValue(VersionCatalog.PATH)
      val merged = TomlEdits.applyTo(before, CatalogMerge.missing(toml("a.toml", before), toml("b.toml", after)))
      val catalog = VersionCatalog(toml("merged.toml", merged))
      val wanted = VersionCatalog(toml("wanted.toml", after))
      assertEquals(wanted.libraries().map { it.alias }.toSet(), catalog.libraries().map { it.alias }.toSet())
      for (bundle in wanted.bundles()) {
        assertEquals(bundle, wanted.bundle(bundle).toSet(), catalog.bundle(bundle).toSet())
      }
      assertEquals("0.13.0", catalog.version("teavm-gradle-plugin"))
      assertTrue(merged.startsWith("# The version catalog lists"))
      assertEquals(emptyList<TextEdit>(), CatalogMerge.missing(toml("c.toml", merged), toml("d.toml", after)))
    }
  }
}
