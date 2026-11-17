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
package org.flixelgdx.intellij.actions

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.flixelgdx.intellij.cli.CliResult
import org.flixelgdx.intellij.cli.FlxgdxNotifier
import org.flixelgdx.intellij.cli.FlxgdxTasks
import org.flixelgdx.intellij.model.ChangeResult
import org.flixelgdx.intellij.model.CommandArgs
import org.flixelgdx.intellij.model.ProjectInfo
import java.nio.file.Path
import java.nio.file.Paths

/**
 * The shared steps of the project actions: read the project, run a command that changes it, report the outcome and
 * refresh the build.
 *
 * Every command goes through [FlxgdxTasks], so progress, cancellation, warnings and failure notifications behave the
 * same as everywhere else in the plugin. All callbacks arrive on the EDT.
 */
object ProjectActions {

  /** The Gradle root of [project], or `null` for a project without a base folder. */
  fun baseDir(project: Project): Path? = project.basePath?.let { Paths.get(it) }

  /**
   * Runs `flxgdx info` and hands the parsed [ProjectInfo] to [then]. Nothing is called when the command fails (a
   * notification explains why) or when the folder is not a FlixelGDX project.
   */
  fun loadInfo(project: Project, then: (ProjectInfo) -> Unit) {
    val dir = baseDir(project) ?: return
    FlxgdxTasks.run(project, "Reading FlixelGDX project", CommandArgs.info(), dir) { result ->
      if (result.isSuccess) {
        val info = ProjectInfo.parse(result.data)
        if (info.inProject) then(info)
      }
    }
  }

  /**
   * Runs a command that changes the build (an add or remove command), then shows what happened: a summary with the
   * notes and suggestions of the packages in one information notification. Unless nothing changed, the files and the
   * Gradle project are refreshed afterwards.
   *
   * @param title the title of the background task and of the notifications, such as "Adding library".
   * @param args the command and its flags.
   */
  fun runChange(project: Project, title: String, args: List<String>) {
    val dir = baseDir(project) ?: return
    FlxgdxTasks.run(project, title, args, dir) { result ->
      if (result.isSuccess) {
        val change = ChangeResult.parse(result.data)
        report(project, title, change)
        if (!change.unchanged) refresh(project)
      }
    }
  }

  /**
   * Runs [args] as a visible task in the project folder and hands the result to [then] on the EDT.
   *
   * @param reportError decides whether a failed result is shown as an error notification; see [FlxgdxTasks.run].
   */
  fun run(
    project: Project,
    title: String,
    args: List<String>,
    reportError: (CliResult) -> Boolean = { true },
    then: (CliResult) -> Unit,
  ) {
    FlxgdxTasks.run(project, title, args, baseDir(project), reportError = reportError) { then(it) }
  }

  /** Refreshes the project files and syncs Gradle. See [GradleRefresh]. */
  fun refresh(project: Project) {
    baseDir(project)?.let { project.service<GradleRefresh>().refresh(it) }
  }

  /** Shows the summary, notes and suggestions of [change] as one information notification. */
  fun report(project: Project, title: String, change: ChangeResult) {
    val lines = ArrayList<String>()
    lines += change.summary()
    lines += change.notes
    for (command in change.suggestions) lines += "Suggested: $command"
    service<FlxgdxNotifier>().info(project, title, lines.joinToString("\n"))
  }
}
