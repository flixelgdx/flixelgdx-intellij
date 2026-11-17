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
 */
package org.flixelgdx.intellij.wizard

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
  fun defaultJavaProject() {
    assertEquals(
      listOf(
        "new", "/work/my-game",
        "--title", "My Game",
        "--company", "My Studio",
        "--app-version", "0.1.0",
        "--package", "com.mystudio.mygame",
        "--width", "640",
        "--height", "360",
        "--platforms", "desktop,html5",
        "--language", "java",
        "--template", "blank",
        "--handbook",
      ),
      base().toArgs().map { it.replace('\\', '/') },
    )
  }

  @Test
  fun kotlinProjectWithAndroidAndEverythingElse() {
    val spec = base().copy(
      width = 1280,
      height = 720,
      language = GameLanguage.KOTLIN,
      android = true,
      libraries = " flixelgdx/flixelgdx-ui , a/b:c ,, ",
      plugins = "x/y",
      suggested = false,
      git = true,
      flixelVersion = " v0.8.0 ",
      force = true,
    )
    assertEquals(
      listOf(
        "new", "/work/my-game",
        "--title", "My Game",
        "--company", "My Studio",
        "--app-version", "0.1.0",
        "--package", "com.mystudio.mygame",
        "--width", "1280",
        "--height", "720",
        "--platforms", "desktop,html5,android",
        "--language", "kotlin",
        "--template", "blank",
        "--libraries", "flixelgdx/flixelgdx-ui,a/b:c",
        "--plugins", "x/y",
        "--flixel-version", "v0.8.0",
        "--no-suggested",
        "--handbook",
        "--git",
        "--force",
      ),
      spec.toArgs().map { it.replace('\\', '/') },
    )
  }

  @Test
  fun everyOptionalFlagFollowsItsSwitch() {
    val off = base().copy(handbook = false, suggested = true, git = false, force = false).toArgs()
    val flags = listOf(
      "--handbook", "--git", "--force", "--no-suggested", "--libraries", "--plugins", "--flixel-version",
    )
    for (flag in flags) {
      assertFalse("$flag should be absent", flag in off)
    }
    val on = base().copy(handbook = true, suggested = false, git = true, force = true).toArgs()
    for (flag in listOf("--handbook", "--git", "--force", "--no-suggested")) {
      assertTrue("$flag should be present", flag in on)
    }
  }

  @Test
  fun platformsKeepTheirOrder() {
    assertEquals("android", base().copy(desktop = false, html5 = false, android = true).platforms.joinToString(","))
    assertEquals("html5,android", base().copy(desktop = false, android = true).platforms.joinToString(","))
    assertEquals("desktop", base().copy(html5 = false).platforms.joinToString(","))
  }

  @Test
  fun blankRepoListsAreLeftOut() {
    val args = base().copy(libraries = " , ", plugins = "").toArgs()
    assertFalse("--libraries" in args)
    assertFalse("--plugins" in args)
  }

  @Test
  fun repoSpecProblems() {
    assertNull(RepoSpecs.problem(""))
    assertNull(RepoSpecs.problem("flixelgdx/flixelgdx-ui, a/b:pkg"))
    assertTrue(RepoSpecs.problem("a/b:")!!.contains("no package name"))
    assertTrue(RepoSpecs.problem("a/b c/d")!!.contains("spaces"))
  }

  @Test
  fun forceOnlyForAFolderWithWizardFiles() {
    assertFalse(TargetFolder.needsForce(emptyList()))
    assertTrue(TargetFolder.needsForce(listOf(".idea")))
    assertTrue(TargetFolder.needsForce(listOf(".idea", "my-game.iml")))
    assertFalse(TargetFolder.needsForce(listOf(".idea", "notes.txt")))
    assertTrue(TargetFolder.hasForeignFiles(listOf(".idea", "notes.txt")))
    assertFalse(TargetFolder.hasForeignFiles(listOf(".idea")))
  }

  @Test
  fun templatesListStartsWithBlank() {
    assertEquals("blank", NewProjectSpec.TEMPLATES.first())
  }
}
