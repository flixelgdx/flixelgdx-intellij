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
package org.flixelgdx.intellij.runconfig

import com.intellij.execution.BeforeRunTask
import com.intellij.execution.BeforeRunTaskProvider
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Key
import org.flixelgdx.intellij.cli.FlxgdxNotifier
import org.flixelgdx.intellij.debugger.DebuggerInstaller
import org.flixelgdx.intellij.debugger.DebuggerLauncher
import org.flixelgdx.intellij.debugger.LaunchOutcome
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration

/** The "Open FlixelGDX Debugger" step in the Before launch list of a run configuration. It keeps no data. */
class DebuggerBeforeRunTask : BeforeRunTask<DebuggerBeforeRunTask>(DebuggerBeforeRunProvider.ID)

/**
 * Adds the "Open FlixelGDX Debugger" step to the Before launch list of Gradle run configurations that start the game in
 * debug mode (`:desktop:debug` and `:html5:debug`).
 *
 * This is the supported way to attach a small action to a run: the step shows up in the run configuration dialog next
 * to Build and friends, the person can switch it off or remove it, and it is saved with the configuration. When the
 * run starts, the step starts the debugger unless one is already running, and then lets the run continue straight
 * away, so the game can connect once it is up (it retries by itself until the debugger is listening).
 *
 * The step never fails the run: a missing or broken debugger only produces a notification.
 */
class DebuggerBeforeRunProvider : BeforeRunTaskProvider<DebuggerBeforeRunTask>() {

  override fun getId(): Key<DebuggerBeforeRunTask> = ID

  override fun getName(): String = NAME

  override fun getDescription(task: DebuggerBeforeRunTask): String = NAME

  override fun isConfigurable(): Boolean = false

  override fun isSingleton(): Boolean = true

  /** Offers the step only for Gradle configurations that run a debug-tools task. */
  override fun createTask(runConfiguration: RunConfiguration): DebuggerBeforeRunTask? {
    val config = runConfiguration as? GradleRunConfiguration ?: return null
    if (!RunConfigPlanner.isDebugTools(config.settings.taskNames.orEmpty())) return null
    return DebuggerBeforeRunTask()
  }

  override fun executeTask(
    context: DataContext,
    configuration: RunConfiguration,
    environment: ExecutionEnvironment,
    task: DebuggerBeforeRunTask,
  ): Boolean {
    val app = ApplicationManager.getApplication()
    if (app.isDispatchThread) {
      app.executeOnPooledThread { start(configuration) }
    } else {
      start(configuration)
    }
    return true
  }

  private fun start(configuration: RunConfiguration) {
    val project = configuration.project
    val notifier = service<FlxgdxNotifier>()
    when (val outcome = DebuggerLauncher.ensureRunning()) {
      is LaunchOutcome.NotInstalled -> DebuggerInstaller.offer(project)
      is LaunchOutcome.Failed ->
        notifier.error(project, NAME, "The debugger at ${outcome.path} could not be started: ${outcome.reason}")
      is LaunchOutcome.AlreadyRunning, is LaunchOutcome.Started -> {}
    }
  }

  companion object {
    /** The id of the step, saved with the run configuration. */
    val ID: Key<DebuggerBeforeRunTask> = Key.create("org.flixelgdx.intellij.OpenDebugger")

    /** The name shown in the Before launch list. */
    const val NAME = "Open FlixelGDX Debugger"
  }
}
