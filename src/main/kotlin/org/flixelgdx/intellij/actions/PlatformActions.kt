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
import com.intellij.openapi.project.Project
import org.flixelgdx.intellij.cli.FlxgdxNotifier
import org.flixelgdx.intellij.model.CommandArgs

/** Tools > FlixelGDX > Add Platform: offers the platforms that are not enabled yet. */
class AddPlatformAction : FlxgdxAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    ProjectActions.loadInfo(project) { info ->
      val options = info.missingPlatforms
      if (options.isEmpty()) {
        service<FlxgdxNotifier>().info(project, TITLE, "Every platform is already enabled.")
        return@loadInfo
      }
      val dialog = ChoiceDialog(project, "Add Platform", "Platform:", options, { it }, okText = "Add")
      if (dialog.showAndGet()) {
        dialog.choice?.let { ProjectActions.runChange(project, TITLE, CommandArgs.addPlatform(it)) }
      }
    }
  }

  private companion object {
    const val TITLE = "Adding platform"
  }
}

/** Tools > FlixelGDX > Remove Platform: offers the enabled platforms and asks whether to delete the module folder. */
class RemovePlatformAction : FlxgdxAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    ProjectActions.loadInfo(project) { info -> choose(project, info.platforms) }
  }

  private fun choose(project: Project, platforms: List<String>) {
    if (platforms.isEmpty()) {
      service<FlxgdxNotifier>().info(project, TITLE, "The project has no platforms to remove.")
      return
    }
    val dialog = ChoiceDialog(
      project,
      "Remove Platform",
      "Platform:",
      platforms,
      { it },
      checkbox = "Also delete the module folder (this cannot be undone)",
      okText = "Remove",
    )
    if (dialog.showAndGet()) {
      dialog.choice?.let { ProjectActions.runChange(project, TITLE, CommandArgs.removePlatform(it, dialog.checked)) }
    }
  }

  private companion object {
    const val TITLE = "Removing platform"
  }
}
