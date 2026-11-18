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
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Renders every language and platform set and compares the result with the golden files in
 * `src/test/resources/golden`, which hold the whole project as text.
 *
 * The golden files started as the expected output of the old project generator. Run the tests with
 * `-Dgolden.update=true` to rewrite them after a deliberate change, then review the diff.
 */
class GameTemplatesTest {

  @Test
  fun everyPlatformSetMatchesItsGoldenFile() {
    val update = System.getProperty("golden.update") == "true"
    for (language in GameLanguage.entries) {
      for (platforms in PLATFORM_SETS) {
        val name = language.id + "-" + platforms.joinToString("-")
        val got = snapshot(GameTemplates.render(data(language, platforms), GameTemplates.DEFAULT))
        val file = GOLDEN_DIR.resolve("$name.txt")
        if (update) {
          Files.writeString(file, got)
          continue
        }
        val want = Files.readString(file)
        if (got != want) fail("$name differs from its golden file:\n" + firstDifference(want, got))
      }
    }
  }

  @Test
  fun indexListsEveryTemplateFile() {
    val root = Path.of("src/main/resources", GameTemplates.ROOT)
    val onDisk = Files.walk(root).use { s ->
      s.filter { Files.isRegularFile(it) }
        .map { root.relativize(it).toString().replace('\\', '/') }
        .filter { it != "index.txt" }
        .sorted()
        .toList()
    }
    assertEquals(onDisk, GameTemplates.index.sorted())
  }

  @Test
  fun templatesAreListedPerLanguage() {
    assertEquals(listOf("blank"), GameTemplates.names(GameLanguage.JAVA))
    assertEquals(listOf("blank"), GameTemplates.names(GameLanguage.KOTLIN))
  }

  @Test
  fun platformModulesOnlyAppearWhenChosen() {
    val paths = GameTemplates.render(data(GameLanguage.JAVA, listOf("html5")), "blank").map { it.path }
    assertTrue(paths.none { it.startsWith("desktop/") || it.startsWith("android/") })
    assertTrue("html5/build.gradle.kts" in paths)
    assertTrue("core/build.gradle.kts" in paths)
  }

  @Test
  fun gradlewIsExecutableAndTheBatchFileUsesCrlf() {
    val files = GameTemplates.render(data(GameLanguage.JAVA, listOf("desktop")), "blank").associateBy { it.path }
    assertTrue(files.getValue("gradlew").executable)
    val bat = files.getValue("gradlew.bat").text()
    assertTrue(bat.contains("\r\n"))
    assertTrue(!bat.replace("\r\n", "").contains("\n"))
  }

  @Test(expected = IllegalArgumentException::class)
  fun unknownTemplateIsRefused() {
    GameTemplates.render(data(GameLanguage.JAVA, listOf("desktop")), "platformer")
  }

  private fun data(language: GameLanguage, platforms: List<String>) = GameTemplateData.of(
    title = "Java Game",
    company = "Test Studio",
    version = "0.1.0",
    pkg = "com.teststudio.javagame",
    language = language,
    platforms = platforms,
    flixelVersion = "1.2.3",
    ktxVersion = "0.4.0",
    folder = "my-game",
    width = 640,
    height = 360,
  )

  /** Writes every file as text in the same format and order as the golden files: folder by folder, like a walk. */
  private fun snapshot(files: List<GameFile>): String {
    val sb = StringBuilder()
    val sorted = files.sortedWith { a, b -> compareSegments(a.path.split('/'), b.path.split('/')) }
    for (file in sorted) {
      val mode = if (file.executable) "755" else "644"
      if (file.path.startsWith("gradlew") || file.path.endsWith(".jar")) {
        sb.append("=== ${file.path} (mode $mode, sha256 ${sha256(file.content)})\n")
        continue
      }
      val text = file.text()
      sb.append("=== ${file.path} (mode $mode)\n").append(text)
      if (text.isNotEmpty() && !text.endsWith("\n")) sb.append('\n')
    }
    return sb.toString()
  }

  private fun compareSegments(a: List<String>, b: List<String>): Int {
    for (i in 0 until minOf(a.size, b.size)) {
      val c = a[i].compareTo(b[i])
      if (c != 0) return c
    }
    return a.size - b.size
  }

  private fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

  private fun firstDifference(want: String, got: String): String {
    val w = want.split('\n')
    val g = got.split('\n')
    for (i in 0 until maxOf(w.size, g.size)) {
      val wl = w.getOrNull(i)
      val gl = g.getOrNull(i)
      if (wl != gl) return "line ${i + 1}:\n  want: $wl\n  got:  $gl"
    }
    return "no difference"
  }

  private companion object {
    val GOLDEN_DIR: Path = Path.of("src/test/resources/golden")

    val PLATFORM_SETS = listOf(
      listOf("desktop"),
      listOf("html5"),
      listOf("android"),
      listOf("desktop", "html5"),
      listOf("desktop", "android"),
      listOf("html5", "android"),
      listOf("desktop", "html5", "android"),
    )
  }
}
