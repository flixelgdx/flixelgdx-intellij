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
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Path

class DebuggerDetectorTest {

  @get:Rule
  val tmp = TemporaryFolder()

  private fun exe(dir: File, name: String = "FlixelDebugger.App"): Path {
    dir.mkdirs()
    val f = File(dir, name)
    f.writeText("#!/bin/sh\n")
    f.setExecutable(true)
    return f.toPath()
  }

  /** Installs a fake debugger into the default Linux folder below [home], with [version] remembered. */
  private fun install(home: File, version: String? = "v1.2.0"): Path {
    val dir = File(home, ".local/share/flixelgdx/debugger")
    val path = exe(dir)
    if (version != null) File(dir, DebuggerPaths.VERSION_FILE).writeText("$version\n")
    return path
  }

  private fun env(path: String?, home: File, windows: Boolean = false) =
    DebuggerEnvironment(path, home.toPath(), windows, mac = false, getenv = { null })

  @Test
  fun settingsWinsOverTheInstallAndPath() {
    val home = tmp.newFolder("home")
    install(home)
    val inPath = tmp.newFolder("bin")
    exe(inPath, "flixelgdx-debugger")
    val custom = exe(tmp.newFolder("custom"))

    val found = DebuggerDetector.detect(custom.toString(), env(inPath.toString(), home))
    assertEquals(DetectedDebugger(custom, null, DebuggerSource.SETTINGS), found)
  }

  @Test
  fun settingsMayNameTheFolder() {
    val home = tmp.newFolder("home")
    val dir = tmp.newFolder("custom")
    val exe = exe(dir)
    assertEquals(
      DetectedDebugger(exe, null, DebuggerSource.SETTINGS),
      DebuggerDetector.detect(dir.toString(), env(null, home)),
    )
  }

  @Test
  fun theInstallComesBeforePathAndKnowsItsVersion() {
    val home = tmp.newFolder("home")
    val installed = install(home, "v1.3.0")
    val inPath = tmp.newFolder("bin")
    exe(inPath, "flixelgdx-debugger")

    val found = DebuggerDetector.detect("", env(inPath.toString(), home))
    assertEquals(DetectedDebugger(installed, "v1.3.0", DebuggerSource.INSTALLED), found)
  }

  @Test
  fun anInstallWithoutAVersionFileHasNoVersion() {
    val home = tmp.newFolder("home")
    install(home, version = null)
    assertNull(DebuggerDetector.detect("", env(null, home))?.version)
  }

  @Test
  fun noInstallFallsThroughToPath() {
    val home = tmp.newFolder("home")
    val inPath = tmp.newFolder("bin")
    val launcher = exe(inPath, "flixelgdx-debugger")

    val found = DebuggerDetector.detect("", env(inPath.toString(), home))
    assertEquals(DetectedDebugger(launcher, null, DebuggerSource.PATH), found)
  }

  @Test
  fun aWrongSettingsPathFallsThrough() {
    val home = tmp.newFolder("home")
    install(home)
    val found = DebuggerDetector.detect("/definitely/not/here", env(null, home))
    assertEquals(DebuggerSource.INSTALLED, found?.source)
  }

  @Test
  fun nothingFoundGivesNull() {
    assertNull(DebuggerDetector.detect("", env("", tmp.newFolder("home"))))
  }
}
