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

import org.flixelgdx.intellij.template.FrameworkSource
import org.flixelgdx.intellij.template.GameLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path

class NewProjectSpecTest {

  private val dir: Path = Path.of("/work/my-game")

  private fun base() = NewProjectSpec(dir, "My Game", "My Studio", "0.1.0", "com.mystudio.mygame")

  @Test
  fun defaultJavaProjectData() {
    val data = base().templateData("0.7.2", "0.7.0")
    assertEquals("My Game", data.title)
    assertEquals("MyGame", data.className)
    assertEquals("my-game", data.slug)
    assertEquals("my-game", data.folder)
    assertEquals(listOf("desktop", "html5"), data.platforms)
    assertEquals(GameLanguage.JAVA, data.language)
    assertEquals("0.7.2", data.frameworkVersion)
    assertEquals(640, data.width)
    assertEquals(360, data.height)
  }

  @Test
  fun kotlinAndroidProjectData() {
    val spec = base().copy(language = GameLanguage.KOTLIN, desktop = false, android = true, width = 1280, height = 720)
    val data = spec.templateData("0.7.2", "0.7.0")
    assertEquals(listOf("html5", "android"), data.platforms)
    assertTrue(data.kotlin)
    assertEquals("0.7.0", data.ktxVersion)
    assertEquals(1280, data.width)
  }

  @Test
  fun typedFrameworkVersionDropsTheTagPrefix() {
    assertNull(base().typedFrameworkVersion)
    assertNull(base().copy(frameworkVersion = "  ").typedFrameworkVersion)
    assertEquals("0.8.0", base().copy(frameworkVersion = " v0.8.0 ").typedFrameworkVersion)
    assertEquals("0.8.0", base().copy(frameworkVersion = "0.8.0").typedFrameworkVersion)
  }

  @Test
  fun clonePathOnlyCountsForALocalClone() {
    val clone = base().copy(source = FrameworkSource.LOCAL_CLONE, frameworkPath = " C:\\dev\\flixelgdx ")
    assertEquals("C:/dev/flixelgdx", clone.templateData("0.7.2", "0.7.2").frameworkPath)
    val local = clone.copy(source = FrameworkSource.MAVEN_LOCAL)
    assertEquals("", local.templateData("0.7.2", "0.7.2").frameworkPath)
  }

  @Test
  fun platformsKeepTheirOrder() {
    assertEquals(listOf("desktop", "html5", "android"), base().copy(android = true).platforms)
    assertEquals(emptyList<String>(), base().copy(desktop = false, html5 = false).platforms)
  }

  @Test
  fun wizardFilesDoNotCountAsContent() {
    assertFalse(TargetFolder.hasForeignFiles(emptyList()))
    assertFalse(TargetFolder.hasForeignFiles(listOf(".idea", "my-game.iml")))
    assertTrue(TargetFolder.hasForeignFiles(listOf(".idea", "notes.txt")))
  }
}
