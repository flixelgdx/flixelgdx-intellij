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

import com.intellij.execution.RunManager
import com.intellij.execution.RunManagerEx
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.flixelgdx.intellij.debugger.DebuggerLocator
import org.flixelgdx.intellij.game.GameProject
import org.flixelgdx.intellij.settings.FlxgdxProjectSettings
import org.flixelgdx.intellij.toml.ProjectToml
import org.jetbrains.plugins.gradle.service.execution.GradleExternalTaskConfigurationType
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Keeps the Gradle run configurations of a FlixelGDX project in line with its enabled platforms, so the green Run and
 * Debug buttons work right after the project opens.
 *
 * Think of it as a waiter who sets the table: [sync] looks at which platforms are enabled, lays out a Run
 * configuration for each, and clears away only the places it set itself when a platform leaves. The decisions come
 * from [RunConfigPlanner]; this class only reads the project and applies the plan.
 *
 * Everything that touches the disk happens on a background dispatcher. Changes to the run
 * configurations are made on the EDT.
 */
@Service(Service.Level.PROJECT)
class RunConfigSync(private val project: Project, private val scope: CoroutineScope) {

  /**
   * Brings the run configurations in line with the platforms of the project.
   *
   * Nothing happens when the user turned off "Create run configurations automatically", when the project is not a
   * FlixelGDX project, or when the platforms cannot be read with confidence. The platforms come from the `platforms`
   * key of `project.toml`.
   *
   * Call it from any thread; it only schedules work.
   */
  fun sync() {
    val settings = project.service<FlxgdxProjectSettings>()
    if (!settings.autoRunConfigs) return
    val base = project.basePath ?: return
    scope.launch {
      val platforms = withContext(Dispatchers.IO) { readPlatforms(Paths.get(base)) } ?: return@launch
      val debugger = withContext(Dispatchers.IO) { service<DebuggerLocator>().find() != null }
      ApplicationManager.getApplication()
        .invokeLater({ apply(base, platforms, debugger) }, ModalityState.nonModal(), project.disposed)
    }
  }

  private fun readPlatforms(dir: Path): List<String>? {
    if (!GameProject.isFlixelDir(dir)) return null
    val text = runCatching { Files.readString(dir.resolve(GameProject.MARKER)) }.getOrNull() ?: return null
    return ProjectToml.platforms(text)
  }

  /** Applies the plan on the EDT. [debuggerInstalled] decides if debug-tools configurations get the debugger step. */
  private fun apply(base: String, platforms: List<String>, debuggerInstalled: Boolean) {
    val settings = project.service<FlxgdxProjectSettings>()
    if (!settings.autoRunConfigs) return
    val manager = RunManager.getInstance(project)
    val type = GradleExternalTaskConfigurationType.getInstance()

    val existing = ArrayList<RunExisting>()
    for (entry in manager.allSettings) {
      val config = entry.configuration as? GradleRunConfiguration ?: continue
      existing += RunExisting(entry.name, config.settings.taskNames.orEmpty())
    }
    val plan = RunConfigPlanner.plan(platforms, existing, settings.tracked, debuggerInstalled)

    for (entry in plan.remove) {
      manager.findConfigurationByTypeAndName(type, entry.name)?.let { manager.removeConfiguration(it) }
    }
    for (spec in plan.create) {
      val created = manager.createConfiguration(spec.name, type.factory)
      val config = created.configuration as GradleRunConfiguration
      config.settings.externalProjectPath = base
      config.rawCommandLine = spec.task
      created.folderName = RunConfigPlanner.FOLDER
      if (spec.openDebugger) {
        val step = DebuggerBeforeRunTask()
        step.isEnabled = true
        RunManagerEx.getInstanceEx(project).setBeforeRunTasks(config, listOf(step))
      }
      manager.addConfiguration(created)
    }
    if (manager.selectedConfiguration == null) {
      RunConfigPlanner.selectionName(platforms)
        ?.let { manager.findConfigurationByTypeAndName(type, it) }
        ?.let { manager.selectedConfiguration = it }
    }
    settings.tracked = plan.tracked
  }
}
