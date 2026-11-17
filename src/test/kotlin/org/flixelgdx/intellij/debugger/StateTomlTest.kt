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
import org.junit.Test
import java.nio.file.Path

class StateTomlTest {

  /** The shape `flxgdx` writes (go-toml v2 uses single quoted strings). */
  private val sample = """
    [tools]
      [tools.debugger]
        version = 'v1.0.1'
        dir = '/home/me/.local/share/flixelgdx/debugger'
        executable = '/home/me/.local/share/flixelgdx/debugger/FlixelDebugger.App'
        shortcuts = ['/home/me/.local/bin/flixelgdx-debugger', '/home/me/.local/share/applications/x.desktop']
  """.trimIndent()

  @Test
  fun readsTheDebuggerEntry() {
    assertEquals(
      InstalledTool(
        "v1.0.1",
        "/home/me/.local/share/flixelgdx/debugger",
        "/home/me/.local/share/flixelgdx/debugger/FlixelDebugger.App",
      ),
      StateToml.tool(sample, "debugger"),
    )
  }

  @Test
  fun readsDoubleQuotedStringsWithEscapes() {
    val text = "[tools.debugger]\nversion = \"v2\"\nexecutable = \"C:\\\\Apps\\\\Debugger\\\\FlixelDebugger.App.exe\"\n"
    assertEquals("C:\\Apps\\Debugger\\FlixelDebugger.App.exe", StateToml.tool(text, "debugger")?.executable)
  }

  @Test
  fun readsLiteralWindowsPaths() {
    val text = "[tools.debugger]\nexecutable = 'C:\\Apps\\FlixelDebugger.App.exe'\n"
    assertEquals("C:\\Apps\\FlixelDebugger.App.exe", StateToml.tool(text, "debugger")?.executable)
  }

  @Test
  fun ignoresOtherToolsUnknownKeysAndComments() {
    val text = """
      # written by flxgdx
      [other]
      executable = '/nope'

      [tools.editor]
      executable = '/editor' # a comment
      [tools.debugger]
      future = 42
      executable = '/dbg # not a comment'
      version = 'v3'
    """.trimIndent()
    assertEquals(InstalledTool("v3", null, "/dbg # not a comment"), StateToml.tool(text, "debugger"))
    assertEquals("/editor", StateToml.tool(text, "editor")?.executable)
  }

  @Test
  fun acceptsQuotedTableNames() {
    val text = "[tools.\"debugger\"]\nexecutable = '/x'\n"
    assertEquals("/x", StateToml.tool(text, "debugger")?.executable)
  }

  @Test
  fun missingEntryOrExecutableGivesNull() {
    assertNull(StateToml.tool("", "debugger"))
    assertNull(StateToml.tool("[tools]\n", "debugger"))
    assertNull(StateToml.tool("[tools.debugger]\nversion = 'v1'\n", "debugger"))
    assertNull(StateToml.tool("[tools.debugger]\nexecutable = ''\n", "debugger"))
  }

  @Test
  fun garbageDoesNotThrow() {
    assertNull(StateToml.tool("[[[\n= =\n[tools.debugger\nexecutable = 'x\n", "debugger"))
  }

  @Test
  fun laterTableWins() {
    val text = "[tools.debugger]\nexecutable = '/old'\n[tools.debugger]\nexecutable = '/new'\n"
    assertEquals("/new", StateToml.tool(text, "debugger")?.executable)
  }
}

class ConfigDirTest {

  private val home = Path.of("/home/me")

  @Test
  fun linuxUsesXdgConfigHomeWhenAbsolute() {
    val env = mapOf("XDG_CONFIG_HOME" to "/custom/cfg")
    assertEquals(Path.of("/custom/cfg"), ConfigDir.resolve(false, false, { env[it] }, home))
  }

  @Test
  fun linuxFallsBackToDotConfig() {
    assertEquals(home.resolve(".config"), ConfigDir.resolve(false, false, { null }, home))
    assertEquals(home.resolve(".config"), ConfigDir.resolve(false, false, { "" }, home))
  }

  @Test
  fun linuxRejectsARelativeXdgValueLikeGoDoes() {
    assertNull(ConfigDir.resolve(false, false, { "relative/cfg" }, home))
  }

  @Test
  fun macUsesApplicationSupport() {
    assertEquals(
      home.resolve("Library").resolve("Application Support"),
      ConfigDir.resolve(false, true, { "/ignored" }, home),
    )
  }

  @Test
  fun windowsUsesAppData() {
    val env = mapOf("AppData" to "/users/me/AppData/Roaming")
    assertEquals(Path.of("/users/me/AppData/Roaming"), ConfigDir.resolve(true, false, { env[it] }, home))
    assertNull(ConfigDir.resolve(true, false, { null }, home))
  }

  @Test
  fun statePathIsInsideTheFlxgdxFolder() {
    assertEquals(Path.of("/c/flxgdx/state.toml"), ConfigDir.statePath(Path.of("/c")))
  }
}
