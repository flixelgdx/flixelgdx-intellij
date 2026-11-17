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
import org.flixelgdx.intellij.model.CommandArgs
import org.flixelgdx.intellij.model.InstalledPackage
import org.flixelgdx.intellij.settings.FlxgdxSettings

/** Shows the Add Library or Add Plugin dialog and runs the command it builds. */
abstract class AddPackageAction(private val kind: String) : FlxgdxAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    ProjectActions.loadInfo(project) { info ->
      val dialog = AddPackageDialog(project, kind, info.modules, service<FlxgdxSettings>().recentRepos)
      if (dialog.showAndGet()) {
        val title = if (kind == InstalledPackage.KIND_PLUGIN) "Adding plugin" else "Adding library"
        ProjectActions.runChange(project, title, dialog.args)
      }
    }
  }
}

/** Tools > FlixelGDX > Add Library. */
class AddLibraryAction : AddPackageAction(InstalledPackage.KIND_LIBRARY)

/** Tools > FlixelGDX > Add Plugin. */
class AddPluginAction : AddPackageAction(InstalledPackage.KIND_PLUGIN)

/** Tools > FlixelGDX > Remove Library or Plugin: lists the installed packages and removes the one that is picked. */
class RemovePackageAction : FlxgdxAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    ProjectActions.loadInfo(project) { info -> choose(project, info.packages) }
  }

  private fun choose(project: Project, packages: List<InstalledPackage>) {
    val dialog = RemovePackageDialog(project, packages)
    if (!dialog.showAndGet()) return
    val pkg = dialog.selected ?: return
    val title = if (pkg.isLibrary) "Removing library" else "Removing plugin"
    ProjectActions.runChange(project, title, CommandArgs.remove(pkg))
  }
}
