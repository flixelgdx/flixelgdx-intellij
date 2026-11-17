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
package org.flixelgdx.intellij.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import org.flixelgdx.intellij.cli.FlxgdxLocator

/**
 * The plugin's application-level settings, stored in `flixelgdx.xml` in the IDE config folder.
 *
 * Changing [executablePath] also clears the cached `flxgdx` location, so the next search sees the new value.
 */
@Service(Service.Level.APP)
@State(name = "FlixelGdxSettings", storages = [Storage("flixelgdx.xml")])
class FlxgdxSettings : SimplePersistentStateComponent<FlxgdxSettings.State>(State()) {

  /** The path of the `flxgdx` executable. An empty value means "find it automatically". */
  var executablePath: String
    get() = state.executablePath.orEmpty()
    set(value) {
      if (state.executablePath.orEmpty() != value) {
        state.executablePath = value
        service<FlxgdxLocator>().invalidate()
      }
    }

  /** The path of the FlixelGDX Debugger executable. An empty value means "find it automatically". */
  var debuggerPath: String
    get() = state.debuggerPath.orEmpty()
    set(value) {
      state.debuggerPath = value
    }

  /** The repositories that were used last, newest first. Dialogs offer them again so they need not be retyped. */
  val recentRepos: List<String> get() = state.recentRepos.toList()

  /** Puts [repo] at the top of [recentRepos] (a repeated entry moves up) and keeps the list short. Blank is ignored. */
  fun rememberRepo(repo: String) {
    val value = repo.trim()
    if (value.isEmpty()) return
    val list = state.recentRepos
    list.remove(value)
    list.add(0, value)
    while (list.size > MAX_RECENT) list.removeAt(list.size - 1)
  }

  /** The data that is written to disk. */
  class State : BaseState() {
    var executablePath by string("")
    var debuggerPath by string("")
    var recentRepos by list<String>()
  }

  companion object {
    private const val MAX_RECENT = 10
  }
}
