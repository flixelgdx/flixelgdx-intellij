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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path

class DebuggerProcessesTest {

  private val exe = Path.of("/home/me/.local/bin/flixelgdx-debugger")

  private fun running(vararg commands: String?, realName: String? = null) =
    DebuggerProcesses.isRunning(commands.asSequence(), exe, realName)

  @Test
  fun matchesTheRealProgramBehindALauncherLink() {
    assertTrue(running("/usr/bin/bash", "/opt/x/FlixelDebugger.App", realName = "FlixelDebugger.App"))
  }

  @Test
  fun matchesTheDefaultProgramNameEvenWithoutAResolvedLink() {
    assertTrue(running("/home/me/.local/share/flixelgdx/debugger/FlixelDebugger.App"))
  }

  @Test
  fun matchesTheLauncherNameItself() {
    assertTrue(running("/home/me/.local/bin/flixelgdx-debugger"))
  }

  @Test
  fun matchesWindowsPathsIgnoringCase() {
    assertTrue(running("C:\\Apps\\FlixelDebugger.App.exe"))
    assertTrue(running("c:\\apps\\flixeldebugger.app.EXE"))
  }

  @Test
  fun otherProgramsDoNotMatch() {
    assertFalse(running("/usr/bin/java", "/usr/bin/FlixelDebugger.Other", null, ""))
    assertFalse(running())
  }
}
