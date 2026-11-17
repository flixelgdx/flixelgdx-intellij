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

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import org.flixelgdx.intellij.cli.FlxgdxNotifier
import org.flixelgdx.intellij.model.CommandArgs
import org.flixelgdx.intellij.model.Handbook
import org.flixelgdx.intellij.model.HandbookResult
import org.flixelgdx.intellij.settings.FlxgdxSettings
import java.nio.file.Files
import java.nio.file.Path

/** Tools > FlixelGDX > Install Handbook: copies the docs of a repository into `handbooks/`. */
class InstallHandbookAction : FlxgdxAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val dialog = HandbookDialog(project, service<FlxgdxSettings>().recentRepos)
    if (!dialog.showAndGet()) return
    ProjectActions.run(project, TITLE, dialog.args) { result ->
      if (result.isSuccess) {
        service<FlxgdxNotifier>().info(project, TITLE, HandbookResult.parse(result.data).summary())
        ProjectActions.baseDir(project)?.let { project.service<GradleRefresh>().refreshFiles(it) }
      }
    }
  }

  private companion object {
    const val TITLE = "Installing handbook"
  }
}

/** Tools > FlixelGDX > Uninstall Handbook: lists the handbooks that `flxgdx` installed and removes the one picked. */
class UninstallHandbookAction : FlxgdxAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val dir = ProjectActions.baseDir(project) ?: return
    ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Looking for handbooks", false) {
      private var found: List<Handbook> = emptyList()

      override fun run(indicator: ProgressIndicator) {
        found = listHandbooks(dir)
      }

      override fun onSuccess() {
        choose(project, found)
      }
    })
  }

  private fun choose(project: Project, handbooks: List<Handbook>) {
    if (handbooks.isEmpty()) {
      service<FlxgdxNotifier>().info(project, TITLE, "No handbooks installed by flxgdx were found.")
      return
    }
    val dialog = ChoiceDialog(
      project,
      "Uninstall Handbook",
      "Handbook:",
      handbooks,
      { it.label },
      okText = "Uninstall",
    )
    val picked = if (dialog.showAndGet()) dialog.choice else null
    if (picked != null) {
      ProjectActions.run(project, TITLE, CommandArgs.uninstallHandbook(picked.repo)) { result ->
        if (result.isSuccess) {
          service<FlxgdxNotifier>().info(project, TITLE, "Removed the ${picked.name} handbook.")
          ProjectActions.baseDir(project)?.let { project.service<GradleRefresh>().refreshFiles(it) }
        }
      }
    }
  }

  /**
   * Reads the marker of every folder inside the handbooks folder. Touches the disk, so it must run on a background
   * thread.
   */
  private fun listHandbooks(root: Path): List<Handbook> {
    val folder = root.resolve(Handbook.FOLDER)
    if (!Files.isDirectory(folder)) return emptyList()
    val found = ArrayList<Handbook>()
    runCatching {
      Files.list(folder).use { stream ->
        stream.filter { Files.isDirectory(it) }.sorted().forEach { dir ->
          val marker = dir.resolve(Handbook.MARKER)
          if (Files.isRegularFile(marker)) {
            val name = dir.fileName.toString()
            val book = runCatching { Handbook.fromMarker(name, Files.readString(marker)) }.getOrNull()
            if (book != null) found += book
          }
        }
      }
    }
    return found
  }

  private companion object {
    const val TITLE = "Removing handbook"
  }
}
