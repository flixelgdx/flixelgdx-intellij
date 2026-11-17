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

import org.junit.Assert.assertEquals
import org.junit.Test

class CommandArgsTest {

  @Test
  fun addFromRepoOmitsBlankValues() {
    assertEquals(listOf("add", "library", "--repo", "o/r"), CommandArgs.addFromRepo("library", " o/r ", "", " "))
    assertEquals(
      listOf("add", "plugin", "--repo", "o/r", "--package", "p", "--version", "v1"),
      CommandArgs.addFromRepo("plugin", "o/r", "p", "v1"),
    )
  }

  @Test
  fun addManualLibrary() {
    assertEquals(
      listOf("add", "library", "--group", "g", "--artifact", "a", "--version", "1", "--to", "core,desktop"),
      CommandArgs.addManual("library", "g", "a", "1", listOf("core", "desktop")),
    )
    assertEquals(
      listOf(
        "add", "library", "--group", "g", "--artifact", "a", "--version", "1", "--to", "core",
        "--scope", "processor", "--alias", "x",
      ),
      CommandArgs.addManual("library", "g", "a", "1", listOf("core"), scope = "processor", alias = "x"),
    )
  }

  @Test
  fun addManualPluginTakesTheIdAndNoScope() {
    assertEquals(
      listOf("add", "plugin", "com.p", "--group", "g", "--artifact", "a", "--version", "1", "--to", "core"),
      CommandArgs.addManual("plugin", "g", "a", "1", listOf("core"), scope = "processor", pluginId = "com.p"),
    )
  }

  @Test
  fun removeUsesTheRepoOrTheAlias() {
    val repo = InstalledPackage("o/r", "ui", "library", "v1", framework = false, manual = false, alias = "")
    assertEquals(listOf("remove", "library", "--repo", "o/r", "--package", "ui"), CommandArgs.remove(repo))
    val plugin = repo.copy(kind = "plugin")
    assertEquals(listOf("remove", "plugin", "--repo", "o/r", "--package", "ui"), CommandArgs.remove(plugin))
    val manual = InstalledPackage("", "tiny", "library", "1", framework = false, manual = true, alias = "tiny-lib")
    assertEquals(listOf("remove", "library", "--alias", "tiny-lib"), CommandArgs.remove(manual))
  }

  @Test
  fun platformCommands() {
    assertEquals(listOf("add", "platform", "html5"), CommandArgs.addPlatform("html5"))
    assertEquals(listOf("remove", "platform", "html5"), CommandArgs.removePlatform("html5", false))
    assertEquals(listOf("remove", "platform", "html5", "--delete-files"), CommandArgs.removePlatform("html5", true))
  }

  @Test
  fun handbookCommands() {
    assertEquals(listOf("install", "handbook", "--repo", "o/r"), CommandArgs.installHandbook("o/r", ""))
    assertEquals(
      listOf("install", "handbook", "--repo", "o/r", "--version", "v1"),
      CommandArgs.installHandbook("o/r", "v1"),
    )
    assertEquals(listOf("uninstall", "handbook", "--repo", "o/r"), CommandArgs.uninstallHandbook("o/r"))
  }

  @Test
  fun upgradeDoctorAndManifestCommands() {
    assertEquals(listOf("upgrade", "--dry-run"), CommandArgs.upgradePlan())
    assertEquals(listOf("upgrade", "--yes"), CommandArgs.upgradeApply())
    assertEquals(listOf("doctor"), CommandArgs.doctor(offline = false, fix = false))
    assertEquals(listOf("doctor", "--offline", "--fix"), CommandArgs.doctor(offline = true, fix = true))
    assertEquals(listOf("manifest", "check", "/tmp/x"), CommandArgs.manifestCheck("/tmp/x"))
    assertEquals(listOf("info"), CommandArgs.info())
  }
}
