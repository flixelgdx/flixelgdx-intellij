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

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import org.flixelgdx.intellij.maven.MavenMetadata
import javax.swing.JComponent

/**
 * One version that Upgrade can change.
 *
 * @property label the name shown to the person, such as "FlixelGDX".
 * @property key the `[versions]` key in the version catalog.
 * @property current the version the catalog holds now.
 * @property latest the latest release on Maven Central, or `null` when it could not be found.
 */
data class VersionChoice(val label: String, val key: String, val current: String, val latest: String?)

/**
 * Shows the current and the latest version of the framework (and of its Kotlin extension, when the game uses it), and
 * lets the person confirm or type another version.
 *
 * @param choices the versions to offer; each field starts with the latest release, or the current version when the
 * latest is unknown.
 */
class UpgradeDialog(project: Project, private val choices: List<VersionChoice>) : DialogWrapper(project, true) {

  private val fields = choices.map { JBTextField(it.latest ?: it.current) }

  /** The versions chosen for each key, set when the dialog is accepted. */
  var chosen: Map<String, String> = emptyMap()
    private set

  init {
    title = "Upgrade FlixelGDX"
    setOKButtonText("Upgrade")
    init()
  }

  override fun createCenterPanel(): JComponent = panel {
    for ((choice, field) in choices.zip(fields)) {
      row("${choice.label}:") {
        label(choice.current)
        label("to")
        cell(field).columns(COLUMNS)
          .comment(if (choice.latest == null) "The latest release could not be found." else "Latest: ${choice.latest}")
      }
    }
  }

  override fun doValidate(): ValidationInfo? {
    for (field in fields) {
      if (!MavenMetadata.isRelease(field.text.trim())) {
        return ValidationInfo("Enter a version that starts with a digit, such as 0.7.2.", field)
      }
    }
    return null
  }

  override fun doOKAction() {
    chosen = choices.zip(fields).associate { (choice, field) -> choice.key to field.text.trim() }
    super.doOKAction()
  }

  private companion object {
    const val COLUMNS = 12
  }
}
