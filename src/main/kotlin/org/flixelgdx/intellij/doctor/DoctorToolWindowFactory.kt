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

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.flixelgdx.intellij.actions.FlxgdxAction
import org.flixelgdx.intellij.cli.FlxgdxProject

/** Creates the "FlixelGDX" tool window, which exists only in FlixelGDX projects. */
class DoctorToolWindowFactory : ToolWindowFactory, DumbAware {

  override suspend fun isApplicableAsync(project: Project): Boolean =
    withContext(Dispatchers.IO) { FlxgdxProject.isFlixelProject(project) }

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val panel = DoctorPanel(project)
    val content = ContentFactory.getInstance().createContent(panel, "Doctor", false)
    content.setDisposer(panel)
    toolWindow.contentManager.addContent(content)
  }
}

/** Tools > FlixelGDX > Run Doctor: opens the tool window and runs `flxgdx doctor`. */
class RunDoctorAction : FlxgdxAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    showDoctorWindow(project)
    project.service<DoctorController>().run()
  }
}
