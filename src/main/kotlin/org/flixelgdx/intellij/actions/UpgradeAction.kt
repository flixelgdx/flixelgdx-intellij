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
import org.flixelgdx.intellij.model.UpgradeResult

/**
 * Tools > FlixelGDX > Upgrade: first asks `flxgdx` for a dry run, shows the planned changes, and applies them with
 * `--yes` only after the person confirms.
 */
class UpgradeAction : FlxgdxAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    ProjectActions.run(project, "Looking for upgrades", CommandArgs.upgradePlan()) { result ->
      if (result.isSuccess) review(project, UpgradeResult.parse(result.data))
    }
  }

  private fun review(project: Project, plan: UpgradeResult) {
    if (plan.upToDate) {
      val lines = arrayListOf("Everything is up to date.")
      if (plan.handbooks.isNotEmpty()) lines += "Handbooks that are behind: " + plan.handbooks.joinToString(", ")
      service<FlxgdxNotifier>().info(project, TITLE, lines.joinToString("\n"))
      return
    }
    if (!UpgradeDialog(project, plan).showAndGet()) return
    ProjectActions.run(project, TITLE, CommandArgs.upgradeApply()) { result ->
      if (result.isSuccess) {
        val done = UpgradeResult.parse(result.data)
        val lines = arrayListOf("Upgraded ${done.updates.size} package(s).")
        lines += done.notes
        service<FlxgdxNotifier>().info(project, TITLE, lines.joinToString("\n"))
        ProjectActions.refresh(project)
      }
    }
  }

  private companion object {
    const val TITLE = "Upgrading packages"
  }
}
