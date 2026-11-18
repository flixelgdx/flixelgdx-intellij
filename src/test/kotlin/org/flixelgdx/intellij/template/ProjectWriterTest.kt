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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.nio.file.Files

class ProjectWriterTest {

  @get:Rule
  val tmp = TemporaryFolder()

  private fun file(path: String, text: String, executable: Boolean = false) =
    GameFile(path, text.toByteArray(), executable)

  @Test
  fun writesNestedFilesAndMarksScriptsExecutable() {
    val dir = tmp.root.toPath().resolve("game")
    ProjectWriter.write(dir, listOf(file("a/b/c.txt", "c"), file("gradlew", "#!/bin/sh", executable = true)))
    assertEquals("c", Files.readString(dir.resolve("a/b/c.txt")))
    assumeTrue(!System.getProperty("os.name").lowercase().contains("win"))
    assertTrue(Files.isExecutable(dir.resolve("gradlew")))
  }

  @Test
  fun existingFilesAreKeptWithoutOverwrite() {
    val dir = tmp.newFolder("game").toPath()
    Files.writeString(dir.resolve("keep.txt"), "mine")
    val written = ProjectWriter.write(dir, listOf(file("keep.txt", "new"), file("new.txt", "n")), overwrite = false)
    assertEquals("mine", Files.readString(dir.resolve("keep.txt")))
    assertEquals(listOf(dir.resolve("new.txt")), written)
  }

  @Test
  fun failureRemovesWhatWasCreated() {
    val dir = tmp.newFolder("game").toPath()
    Files.writeString(dir.resolve("blocker"), "a file where a folder is needed")
    try {
      ProjectWriter.write(dir, listOf(file("first/one.txt", "1"), file("blocker/two.txt", "2")))
      throw AssertionError("expected an IOException")
    } catch (expected: IOException) {
      // The write fails because blocker is a file.
    }
    assertFalse(Files.exists(dir.resolve("first")))
    assertTrue(Files.exists(dir.resolve("blocker")))
  }

  @Test(expected = IOException::class)
  fun pathsOutsideTheFolderAreRefused() {
    ProjectWriter.write(tmp.newFolder("game").toPath(), listOf(file("../escape.txt", "x")))
  }
}
