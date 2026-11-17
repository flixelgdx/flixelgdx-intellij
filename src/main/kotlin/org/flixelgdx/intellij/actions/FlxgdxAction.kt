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

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import org.flixelgdx.intellij.cli.FlxgdxProject
import java.nio.file.Paths

/**
 * The base of every FlixelGDX project action: it is visible and enabled only in a FlixelGDX project.
 *
 * `update()` runs on a background thread, so the single file check for `project.toml` is safe there. Subclasses
 * implement [actionPerformed] and must start any slow work as a background task.
 */
abstract class FlxgdxAction : AnAction(), DumbAware {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val project = e.project
    e.presentation.isEnabledAndVisible = project != null && FlxgdxProject.isFlixelProject(project)
  }
}

/**
 * The "FlixelGDX" menu that holds the project actions. It appears under Tools, and in the project view menu when the
 * project root is selected, and only in FlixelGDX projects.
 */
class FlxgdxActionGroup : DefaultActionGroup(), DumbAware {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val project = e.project
    var visible = project != null && FlxgdxProject.isFlixelProject(project)
    if (visible && e.place == ActionPlaces.PROJECT_VIEW_POPUP) {
      val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
      val root = project?.basePath?.let { runCatching { Paths.get(it) }.getOrNull() }
      visible = root != null && files?.singleOrNull()?.let { runCatching { it.toNioPath() }.getOrNull() } == root
    }
    e.presentation.isEnabledAndVisible = visible
  }
}
