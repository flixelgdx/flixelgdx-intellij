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

/**
 * One run configuration the plugin wants to exist.
 *
 * @property platform the platform it belongs to (`desktop`, `html5` or `android`).
 * @property name the name shown in the run configuration list.
 * @property task the Gradle task it runs, with its project path (for example `:desktop:run`).
 * @property openDebugger whether it should get the "Open FlixelGDX Debugger" before-launch task when it is created.
 * The planner sets it only for debug-tools configurations, and only when the debugger is installed.
 */
data class RunSpec(val platform: String, val name: String, val task: String, val openDebugger: Boolean = false)

/**
 * A run configuration that already exists in the IDE, reduced to what the planner compares.
 *
 * @property name the name of the configuration.
 * @property tasks its Gradle task list.
 */
data class RunExisting(val name: String, val tasks: List<String>)

/** A run configuration the plugin created earlier, as remembered in the project settings. */
data class RunTracked(val platform: String, val name: String, val task: String)

/**
 * What to do to bring the run configurations in line with the enabled platforms.
 *
 * @property create the configurations to add.
 * @property remove the configurations to delete: the plugin created them for a platform that is gone, and the user did
 * not change them.
 * @property tracked the new list of configurations the plugin remembers it created.
 */
data class RunPlan(val create: List<RunSpec>, val remove: List<RunTracked>, val tracked: List<RunTracked>) {
  /** Whether applying the plan would change nothing in the IDE. */
  val isEmpty: Boolean get() = create.isEmpty() && remove.isEmpty()
}

/**
 * Decides which run configurations to add or remove. It touches no IDE state, so it is easy to test.
 *
 * Think of it as a careful gardener: it plants what is missing, only pulls up what it planted itself for a bed that no
 * longer exists, and never touches anything the user has changed.
 */
object RunConfigPlanner {

  /** The folder of the run configuration list that holds the configurations of the plugin. */
  const val FOLDER = "FlixelGDX"

  /** The configurations of one platform, in the order they are listed. */
  fun specs(platform: String): List<RunSpec> = when (platform) {
    "desktop" -> listOf(
      RunSpec(platform, "Desktop", ":desktop:run"),
      RunSpec(platform, "Desktop (Debug Tools)", ":desktop:debug"),
    )
    "html5" -> listOf(
      RunSpec(platform, "Web", ":html5:run"),
      RunSpec(platform, "Web (Debug Tools)", ":html5:debug"),
    )
    "android" -> listOf(RunSpec(platform, "Android", ":android:installDebug"))
    else -> emptyList()
  }

  /** The Gradle tasks that start the game in the framework's debug mode, which the debugger connects to. */
  val DEBUG_TASKS = listOf(":desktop:debug", ":html5:debug")

  /** Whether [tasks] (the task list of a Gradle run configuration) contains a debug-tools task. */
  fun isDebugTools(tasks: List<String>): Boolean = tasks.any { it in DEBUG_TASKS }

  /** The configurations wanted for [platforms]: Desktop, Web, then Android, whatever order the input has. */
  fun desired(platforms: List<String>): List<RunSpec> =
    ORDER.filter { it in platforms }.flatMap { specs(it) }

  /**
   * Compares the [desired] configurations with what exists and what the plugin created before.
   *
   * - A configuration is created only when no existing one has its name or already runs exactly its task, and the
   *   plugin has not created it before (so one the user deleted does not come back).
   * - A configuration the plugin created is removed only when its platform is not in [platforms] any more, it still
   *   exists under the same name, and its task list is still exactly the task the plugin gave it.
   * - A remembered configuration for a removed platform that is gone or was edited is simply forgotten.
   *
   * - A configuration that is created and runs a debug-tools task is marked to open the debugger with the run, but only
   *   when [debuggerInstalled] is `true`. Existing configurations are never given the task, because the planner cannot
   *   tell a configuration the user left alone from one they set up on purpose.
   *
   * @param platforms the enabled platforms.
   * @param existing the Gradle run configurations of the project.
   * @param tracked the configurations the plugin remembers it created.
   * @param debuggerInstalled whether the FlixelGDX Debugger was found on this computer.
   */
  fun plan(
    platforms: List<String>,
    existing: List<RunExisting>,
    tracked: List<RunTracked>,
    debuggerInstalled: Boolean = false,
  ): RunPlan {
    val wanted = desired(platforms)
    val create = ArrayList<RunSpec>()
    for (spec in wanted) {
      val taken = existing.any { it.name == spec.name || it.tasks == listOf(spec.task) }
      val remembered = tracked.any { it.name == spec.name }
      if (taken || remembered) continue
      create += spec.copy(openDebugger = debuggerInstalled && isDebugTools(listOf(spec.task)))
    }

    val remove = ArrayList<RunTracked>()
    val kept = ArrayList<RunTracked>()
    for (entry in tracked) {
      if (entry.platform in platforms) {
        kept += entry
        continue
      }
      val current = existing.firstOrNull { it.name == entry.name }
      if (current != null && current.tasks == listOf(entry.task)) remove += entry
    }
    for (spec in create) kept += RunTracked(spec.platform, spec.name, spec.task)
    return RunPlan(create, remove, kept)
  }

  /**
   * Like [plan], but returns a plan that changes nothing (and keeps [tracked] as it is) when [enabled] is `false`,
   * which is how the "Create run configurations automatically" setting switches the feature off.
   */
  fun planIfEnabled(
    enabled: Boolean,
    platforms: List<String>,
    existing: List<RunExisting>,
    tracked: List<RunTracked>,
    debuggerInstalled: Boolean = false,
  ): RunPlan =
    if (enabled) plan(platforms, existing, tracked, debuggerInstalled) else RunPlan(emptyList(), emptyList(), tracked)

  /**
   * The name of the configuration to select when nothing is selected: Desktop when it is enabled, otherwise the first
   * of [platforms]' configurations, or `null` when there are none.
   */
  fun selectionName(platforms: List<String>): String? = desired(platforms).firstOrNull()?.name

  private val ORDER = listOf("desktop", "html5", "android")
}
