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
package org.flixelgdx.intellij.cli

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Path

class FlxgdxLocatorTest {

  @get:Rule
  val tmp = TemporaryFolder()

  private fun exe(dir: File, executable: Boolean = true): Path {
    dir.mkdirs()
    val f = File(dir, "flxgdx")
    f.writeText("#!/bin/sh\n")
    f.setExecutable(executable)
    return f.toPath()
  }

  private fun env(path: String?, home: File, mac: Boolean = false, gopath: String? = null) =
    LocatorEnvironment(path, home.toPath(), windows = false, mac = mac, gopath = gopath)

  @Test
  fun settingsPathWinsOverEverything() {
    val home = tmp.newFolder("home")
    val inPath = tmp.newFolder("bin")
    exe(inPath)
    exe(File(home, "go/bin"))
    val custom = exe(tmp.newFolder("custom"))

    val found = FlxgdxLocator.locate(custom.toString(), env(inPath.toString(), home))
    assertEquals(LocatedCli(custom, LocationSource.SETTINGS), found)
  }

  @Test
  fun settingsMayNameTheFolder() {
    val home = tmp.newFolder("home")
    val dir = tmp.newFolder("custom")
    val exe = exe(dir)
    assertEquals(LocatedCli(exe, LocationSource.SETTINGS), FlxgdxLocator.locate(dir.toString(), env(null, home)))
  }

  @Test
  fun invalidSettingsFallsThroughToPath() {
    val home = tmp.newFolder("home")
    val inPath = tmp.newFolder("bin")
    val exe = exe(inPath)
    val found = FlxgdxLocator.locate("/definitely/not/here", env(inPath.toString(), home))
    assertEquals(LocatedCli(exe, LocationSource.PATH), found)
  }

  @Test
  fun pathIsSearchedInOrder() {
    val home = tmp.newFolder("home")
    val first = tmp.newFolder("first")
    val second = tmp.newFolder("second")
    exe(second)
    val firstExe = exe(first)
    val pathVar = listOf(first, second).joinToString(File.pathSeparator)
    assertEquals(LocatedCli(firstExe, LocationSource.PATH), FlxgdxLocator.locate("", env(pathVar, home)))
  }

  @Test
  fun nonExecutableFilesAreSkipped() {
    val home = tmp.newFolder("home")
    val plain = tmp.newFolder("plain")
    val good = tmp.newFolder("good")
    exe(plain, executable = false)
    val goodExe = exe(good)
    val pathVar = listOf(plain, good).joinToString(File.pathSeparator)
    assertEquals(LocatedCli(goodExe, LocationSource.PATH), FlxgdxLocator.locate("", env(pathVar, home)))
  }

  @Test
  fun commonFoldersAreTriedAfterPath() {
    val home = tmp.newFolder("home")
    val goBin = exe(File(home, "go/bin"))
    exe(File(home, ".local/bin"))
    assertEquals(LocatedCli(goBin, LocationSource.COMMON_FOLDER), FlxgdxLocator.locate("  ", env(null, home)))
  }

  @Test
  fun localBinIsFoundWhenGoBinIsEmpty() {
    val home = tmp.newFolder("home")
    val local = exe(File(home, ".local/bin"))
    assertEquals(LocatedCli(local, LocationSource.COMMON_FOLDER), FlxgdxLocator.locate("", env("", home)))
  }

  @Test
  fun gopathBinIsChecked() {
    val home = tmp.newFolder("home")
    val gopath = tmp.newFolder("gopath")
    val exe = exe(File(gopath, "bin"))
    val found = FlxgdxLocator.locate("", env(null, home, gopath = gopath.toString()))
    assertEquals(LocatedCli(exe, LocationSource.COMMON_FOLDER), found)
  }

  @Test
  fun nothingFoundReturnsNull() {
    assertNull(FlxgdxLocator.locate("", env("", tmp.newFolder("home"))))
  }

  @Test
  fun commonFoldersDependOnThePlatform() {
    val home = tmp.newFolder("home").toPath()
    val linux = FlxgdxLocator.commonFolders(LocatorEnvironment(null, home, windows = false, mac = false))
    val mac = FlxgdxLocator.commonFolders(LocatorEnvironment(null, home, windows = false, mac = true))
    val win = FlxgdxLocator.commonFolders(LocatorEnvironment(null, home, windows = true, mac = false))
    assertEquals(listOf(home.resolve("go/bin"), home.resolve(".local/bin")), linux)
    assertEquals(true, mac.contains(Path.of("/opt/homebrew/bin")))
    assertEquals(true, win.contains(home.resolve("scoop/shims")))
  }
}
