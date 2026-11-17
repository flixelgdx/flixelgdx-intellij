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
package org.flixelgdx.intellij.doctor

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.flixelgdx.intellij.actions.ProjectActions
import org.flixelgdx.intellij.cli.FlxgdxNotifier
import org.flixelgdx.intellij.model.CommandArgs
import org.flixelgdx.intellij.model.DoctorResult
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Runs `flxgdx doctor` for one project and keeps the latest result, so the "FlixelGDX" tool window and the Run Doctor
 * action share the same state.
 *
 * Think of it as the receptionist of the tool window: the toolbar buttons ask it to run, and the window listens for it
 * to announce a new result. [run] and the listeners live on the EDT. The command itself runs as a background task.
 */
@Service(Service.Level.PROJECT)
class DoctorController(private val project: Project) {

  private val listeners = CopyOnWriteArrayList<() -> Unit>()

  /** Whether to pass `--offline`, which skips the checks that need the network. */
  @Volatile var offline: Boolean = false

  /** The latest result, or `null` before the first run. */
  var result: DoctorResult? = null
    private set

  /** Whether a doctor run is in progress. */
  var running: Boolean = false
    private set

  /** Calls [listener] on the EDT whenever [result] or [running] changes, until [parent] is disposed. */
  fun addListener(parent: Disposable, listener: () -> Unit) {
    listeners += listener
    Disposer.register(parent) { listeners -= listener }
  }

  /**
   * Runs `flxgdx doctor` and shows the checks.
   *
   * With [fix], it first runs `doctor --fix` and then runs a plain `doctor` again, so the window shows the state after
   * the repairs. When a repair changed the project files, the Gradle project is synced, and the repaired checks are
   * listed in an information notification. A run that is already in progress is not started twice.
   *
   * Call it on the EDT.
   */
  fun run(fix: Boolean = false) {
    if (running) return
    running = true
    changed()
    // A doctor run that finds an error still has a result to show, so only a run without one is reported as a failure.
    ProjectActions.run(project, TITLE, CommandArgs.doctor(offline, fix), reportError = { it.data == null }) { cli ->
      val data = cli.data
      if (data != null) {
        val parsed = DoctorResult.parse(data)
        if (fix && parsed.fixedCount > 0) {
          val fixed = parsed.checks.filter { it.fixed }.joinToString("\n") { it.message }
          service<FlxgdxNotifier>().info(project, TITLE, "Applied ${parsed.fixedCount} fix(es):\n$fixed")
          ProjectActions.refresh(project)
        }
        if (!fix) result = parsed
      }
      running = false
      if (fix && data != null) {
        run(fix = false)
      } else {
        changed()
      }
    }
  }

  private fun changed() {
    for (listener in listeners) listener()
  }

  private companion object {
    const val TITLE = "Running flxgdx doctor"
  }
}
