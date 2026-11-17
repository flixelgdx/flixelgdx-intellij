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

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.EnvironmentUtil
import org.flixelgdx.intellij.settings.FlxgdxSettings
import java.nio.file.Path

/**
 * Finds the FlixelGDX Debugger on this computer, using the path in the settings, then the install that `flxgdx`
 * recorded, then `PATH`. See [DebuggerDetector] for the order.
 *
 * Detection reads files, so call [find] off the EDT.
 *
 * Example:
 * ```
 * val debugger = service<DebuggerLocator>().find()
 * ```
 */
@Service(Service.Level.APP)
class DebuggerLocator {

  /**
   * Searches now (nothing is cached, because installing or deleting the debugger must show up at once).
   *
   * @param configured the path to try first. It defaults to the one saved in the settings; the settings page passes
   * what is typed in its field so the answer follows the field before it is applied.
   */
  fun find(configured: String = service<FlxgdxSettings>().debuggerPath): DetectedDebugger? =
    DebuggerDetector.detect(configured, environment())

  private fun environment() = DebuggerEnvironment(
    pathVariable = EnvironmentUtil.getValue("PATH"),
    home = Path.of(System.getProperty("user.home")),
    windows = SystemInfo.isWindows,
    mac = SystemInfo.isMac,
    getenv = { EnvironmentUtil.getValue(it) },
  )
}
