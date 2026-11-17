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
import org.flixelgdx.intellij.runconfig.RunTracked

/**
 * The plugin's project-level settings, stored in `flixelgdx.xml` in the project's `.idea` folder.
 *
 * Besides the user's choice about run configurations it remembers which run configurations the plugin created, so it
 * can tell them apart from the ones the user made.
 */
@Service(Service.Level.PROJECT)
@State(name = "FlixelGdxProjectSettings", storages = [Storage("flixelgdx.xml")])
class FlxgdxProjectSettings : SimplePersistentStateComponent<FlxgdxProjectSettings.State>(State()) {

  /** Whether run configurations are created and kept in sync automatically. On by default. */
  var autoRunConfigs: Boolean
    get() = state.autoRunConfigs
    set(value) {
      state.autoRunConfigs = value
    }

  /** The run configurations the plugin created, in creation order. */
  var tracked: List<RunTracked>
    get() = state.tracked.map { RunTracked(it.platform.orEmpty(), it.name.orEmpty(), it.task.orEmpty()) }
    set(value) {
      state.tracked = value.mapTo(ArrayList()) { entry ->
        TrackedState().apply {
          platform = entry.platform
          name = entry.name
          task = entry.task
        }
      }
    }

  /** The data that is written to disk. */
  class State : BaseState() {
    var autoRunConfigs by property(true)
    var tracked by list<TrackedState>()
  }

  /** One remembered run configuration. */
  class TrackedState : BaseState() {
    var platform by string()
    var name by string()
    var task by string()
  }
}
