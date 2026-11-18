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
package org.flixelgdx.intellij.debugger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DebuggerReleaseTest {

  @get:Rule
  val tmp = TemporaryFolder()

  @Test
  fun assetNamesFollowTheReleaseFiles() {
    assertEquals("flixelgdx-debugger-linux-x64.zip", DebuggerRelease.asset("Linux", "amd64"))
    assertEquals("flixelgdx-debugger-linux-arm64.zip", DebuggerRelease.asset("Linux", "aarch64"))
    assertEquals("flixelgdx-debugger-win-x64.zip", DebuggerRelease.asset("Windows 11", "amd64"))
    assertEquals("flixelgdx-debugger-win-arm64.zip", DebuggerRelease.asset("Windows 11", "aarch64"))
    assertEquals("flixelgdx-debugger-osx-arm64.zip", DebuggerRelease.asset("Mac OS X", "aarch64"))
    assertNull(DebuggerRelease.asset("Mac OS X", "x86_64"))
    assertNull(DebuggerRelease.asset("FreeBSD", "amd64"))
    assertNull(DebuggerRelease.asset("Linux", "riscv64"))
  }

  @Test
  fun urlsAndTags() {
    assertEquals(
      "https://github.com/flixelgdx/flixelgdx-debugger/releases/latest/download/a.zip",
      DebuggerRelease.latestDownloadUrl("a.zip"),
    )
    val latest = "https://github.com/flixelgdx/flixelgdx-debugger/releases/tag/v1.0.1"
    assertEquals("v1.0.1", DebuggerRelease.tagFrom(latest))
    assertEquals("v2", DebuggerRelease.tagFrom("https://github.com/o/r/releases/download/v2/a.zip"))
    assertNull(DebuggerRelease.tagFrom("https://github.com/o/r/releases"))
  }

  @Test
  fun checksumsAreReadLikeSha256sum() {
    val hash = "a".repeat(64)
    val text = "$hash  one.zip\n${"B".repeat(64)} *two.zip\nnot a line\n"
    assertEquals(mapOf("one.zip" to hash, "two.zip" to "b".repeat(64)), DebuggerRelease.parseChecksums(text))
  }

  @Test
  fun installFoldersPerSystem() {
    val home = Path.of("/home/me")
    fun env(windows: Boolean, mac: Boolean, vars: Map<String, String> = emptyMap()) =
      DebuggerEnvironment(null, home, windows, mac) { vars[it] }
    assertEquals(home.resolve(".local/share/flixelgdx/debugger"), DebuggerPaths.installDir(env(false, false)))
    assertEquals(
      Path.of("/data/flixelgdx/debugger"),
      DebuggerPaths.installDir(env(false, false, mapOf("XDG_DATA_HOME" to "/data"))),
    )
    assertEquals(
      home.resolve(".local/share/flixelgdx/debugger"),
      DebuggerPaths.installDir(env(false, false, mapOf("XDG_DATA_HOME" to "relative"))),
    )
    assertEquals(
      home.resolve("Library/Application Support/FlixelGDX/Debugger"),
      DebuggerPaths.installDir(env(false, true)),
    )
    assertEquals(home.resolve("AppData/Local/FlixelGDX/Debugger"), DebuggerPaths.installDir(env(true, false)))
  }

  @Test
  fun installUnpacksAndSwapsInTheNewFolder() {
    val dir = tmp.root.toPath().resolve("debugger")
    Files.createDirectories(dir)
    Files.writeString(dir.resolve("old.txt"), "old")
    DebuggerUnpacker.install(zip("FlixelDebugger.App" to "#!/bin/sh", "lib/a.txt" to "a"), dir, "FlixelDebugger.App")
    assertTrue(Files.isRegularFile(dir.resolve("FlixelDebugger.App")))
    assertEquals("a", Files.readString(dir.resolve("lib/a.txt")))
    assertFalse(Files.exists(dir.resolve("old.txt")))
    val left = Files.list(tmp.root.toPath()).use { s -> s.map { it.fileName.toString() }.toList() }
    assertEquals(listOf("debugger"), left.filter { !it.endsWith(".zip") })
  }

  @Test
  fun aDownloadWithoutTheProgramKeepsTheOldInstall() {
    val dir = tmp.root.toPath().resolve("debugger")
    Files.createDirectories(dir)
    Files.writeString(dir.resolve("FlixelDebugger.App"), "old")
    try {
      DebuggerUnpacker.install(zip("readme.txt" to "x"), dir, "FlixelDebugger.App")
      throw AssertionError("expected an IOException")
    } catch (expected: IOException) {
      // The archive does not hold the program.
    }
    assertEquals("old", Files.readString(dir.resolve("FlixelDebugger.App")))
  }

  @Test(expected = IOException::class)
  fun entriesThatLeaveTheFolderAreRefused() {
    DebuggerUnpacker.extract(zip("../evil.txt" to "x"), tmp.newFolder("out").toPath())
  }

  @Test
  fun desktopEntryQuotesPathsWithSpaces() {
    val entry = DebuggerLaunchers.desktopEntry(Path.of("/opt/my apps/FlixelDebugger.App"), null)
    assertTrue(entry.contains("Exec=\"/opt/my apps/FlixelDebugger.App\"\n"))
    assertFalse(entry.contains("Icon="))
  }

  private fun zip(vararg entries: Pair<String, String>): Path {
    val file = tmp.newFile("test-${System.nanoTime()}.zip").toPath()
    ZipOutputStream(Files.newOutputStream(file)).use { out ->
      for ((name, text) in entries) {
        out.putNextEntry(ZipEntry(name))
        out.write(text.toByteArray())
        out.closeEntry()
      }
    }
    return file
  }
}
