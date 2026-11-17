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
 */
package org.flixelgdx.intellij.wizard

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.flixelgdx.intellij.cli.FlxgdxNotifier
import org.flixelgdx.intellij.cli.FlxgdxTasks
import org.flixelgdx.intellij.runconfig.RunConfigSync
import org.jetbrains.plugins.gradle.service.project.open.linkAndSyncGradleProject
import java.nio.file.Files
import java.nio.file.Path

/**
 * Creates a FlixelGDX project with `flxgdx new` inside the project the wizard just opened, then imports it as a
 * Gradle project.
 *
 * Think of it as a builder who arrives at an empty plot: [create] asks `flxgdx` to put up the house, and once that is
 * done the Gradle import is started so the IDE learns about the modules.
 */
@Service(Service.Level.PROJECT)
class NewProjectRunner(private val project: Project, private val scope: CoroutineScope) {

  /**
   * Runs `flxgdx new` for [spec] under a cancelable background task of this project, and imports the result.
   *
   * The task title is "Creating FlixelGDX project". When `flxgdx` is missing or too old, the usual "flxgdx is not
   * ready" notification is shown instead. Once the run succeeds, the notes of the installed packages are shown in
   * one notification and the Gradle import starts. Warnings and failures are reported by [FlxgdxTasks.run].
   *
   * Call it from any thread; it only schedules work.
   */
  fun create(spec: NewProjectSpec) {
    val dir = spec.dir
    FlxgdxTasks.run(
      project = project,
      title = TITLE,
      args = spec.toArgs(),
      workDir = dir.parent,
      onFinished = { result ->
        if (result.isSuccess) {
          val parsed = NewProjectResult.parse(result.data)
          if (parsed.notes.isNotEmpty()) {
            service<FlxgdxNotifier>().info(project, TITLE, parsed.notes.joinToString("\n"))
          }
          importGradle(dir)
          project.service<RunConfigSync>().sync()
        }
      },
    )
  }

  /**
   * Prepares the arguments for a run into [dir]: it lists the folder on a background thread and sets
   * [NewProjectSpec.force] only when the folder holds nothing but the wizard's own files.
   *
   * This is the entry point the wizard uses. It blocks on disk access, so it hops to a pooled thread first.
   */
  fun createIn(spec: NewProjectSpec) {
    ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Preparing FlixelGDX project", false) {
      private var prepared = spec

      override fun run(indicator: ProgressIndicator) {
        prepared = spec.copy(force = TargetFolder.needsForce(listEntries(spec.dir)))
      }

      override fun onSuccess() {
        create(prepared)
      }
    })
  }

  private fun listEntries(dir: Path): List<String> {
    if (!Files.isDirectory(dir)) return emptyList()
    return runCatching { Files.list(dir).use { s -> s.map { it.fileName.toString() }.toList() } }
      .getOrDefault(emptyList())
  }

  private fun importGradle(dir: Path) {
    scope.launch {
      withContext(Dispatchers.IO) {
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(dir)
          ?.let { VfsUtil.markDirtyAndRefresh(false, true, true, it) }
      }
      linkAndSyncGradleProject(project, dir.toString())
    }
  }

  companion object {
    private const val TITLE = "Creating FlixelGDX project"
  }
}
