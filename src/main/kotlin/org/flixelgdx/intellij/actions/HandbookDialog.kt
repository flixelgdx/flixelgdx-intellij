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
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import org.flixelgdx.intellij.model.CommandArgs
import org.flixelgdx.intellij.settings.FlxgdxSettings
import javax.swing.JComponent

/**
 * The dialog behind Install Handbook: the repository whose docs to copy into `handbooks/`, and an optional release.
 *
 * When the dialog is accepted, [args] holds the finished command and the repository is added to the recent list.
 *
 * @param recents the repositories used recently, offered in the repository field.
 */
class HandbookDialog(project: Project, recents: List<String>) : DialogWrapper(project, true) {

  private val repoBox = repoCombo(recents)
  private val versionField = JBTextField()

  /** The finished command, set when the dialog is accepted. */
  var args: List<String> = emptyList()
    private set

  init {
    title = "Install Handbook"
    versionField.emptyText.text = "the installed release, else the latest"
    init()
  }

  override fun createCenterPanel(): JComponent = panel {
    row("Repository:") {
      cell(repoBox).align(AlignX.FILL)
        .comment("owner/repo, a GitHub URL, or a folder. The repository must declare a handbook.")
    }
    row("Version:") {
      cell(versionField).align(AlignX.FILL)
        .comment("A release tag such as v1.2.0. Leave empty to match the installed package.")
    }
  }

  override fun doValidate(): ValidationInfo? =
    if (repoBox.text().isEmpty()) ValidationInfo("Enter a repository.", repoBox) else null

  override fun doOKAction() {
    val repo = repoBox.text()
    service<FlxgdxSettings>().rememberRepo(repo)
    args = CommandArgs.installHandbook(repo, versionField.text)
    super.doOKAction()
  }
}
