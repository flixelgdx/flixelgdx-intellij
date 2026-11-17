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
package org.flixelgdx.intellij.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import org.flixelgdx.intellij.cli.FlxgdxCli
import org.flixelgdx.intellij.cli.FlxgdxLocator
import org.flixelgdx.intellij.debugger.DebuggerInstaller
import org.flixelgdx.intellij.debugger.DebuggerLocator
import org.flixelgdx.intellij.debugger.DebuggerSource
import java.nio.file.Paths
import javax.swing.event.DocumentEvent

/** The settings page under Tools > FlixelGDX. */
class FlxgdxConfigurable : BoundSearchableConfigurable("FlixelGDX", "org.flixelgdx.intellij.settings") {

  private lateinit var pathField: TextFieldWithBrowseButton
  private lateinit var debuggerField: TextFieldWithBrowseButton
  private val resultLabel = JBLabel()
  private val debuggerLabel = JBLabel()
  private val reinstall = JBCheckBox("Reinstall")

  @Volatile
  private var detectRound = 0

  override fun createPanel(): DialogPanel {
    val settings = service<FlxgdxSettings>()
    return panel {
      row("Executable path:") {
        pathField = textFieldWithBrowseButton(
          FileChooserDescriptorFactory.createSingleFileOrExecutableAppDescriptor().withTitle("Select flxgdx"),
        ).bindText(settings::executablePath)
          .align(AlignX.FILL)
          .comment("Leave empty to find flxgdx automatically on PATH and in the usual install folders.")
          .component
        button("Test") { test() }
      }
      row {
        cell(resultLabel)
      }
      group("FlixelGDX Debugger") {
        row("Debugger executable:") {
          debuggerField = textFieldWithBrowseButton(
            FileChooserDescriptorFactory.createSingleFileOrExecutableAppDescriptor()
              .withTitle("Select the FlixelGDX Debugger"),
          ).bindText(settings::debuggerPath)
            .align(AlignX.FILL)
            .comment(
              "Leave empty to use the install that flxgdx recorded, then flixelgdx-debugger on PATH.",
            )
            .component
          debuggerField.textField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
              detectDebugger()
            }
          })
          button("Install Debugger") { installDebugger() }
          cell(reinstall)
        }
        row {
          cell(debuggerLabel)
        }
      }
    }.also { detectDebugger() }
  }

  /** Looks for the debugger on a pooled thread (it reads files) and shows what was found under the field. */
  private fun detectDebugger() {
    val typed = debuggerField.text.trim()
    val round = ++detectRound
    debuggerLabel.text = "Looking for the debugger..."
    ApplicationManager.getApplication().executeOnPooledThread {
      val found = service<DebuggerLocator>().find(typed)
      val text = if (found == null) {
        "The debugger was not found."
      } else {
        val origin = when (found.source) {
          DebuggerSource.SETTINGS -> "from the path above"
          DebuggerSource.CLI_STATE -> "installed by flxgdx"
          DebuggerSource.PATH -> "from PATH"
        }
        val version = found.version?.let { " $it" }.orEmpty()
        "Found$version ($origin): ${found.path}"
      }
      ApplicationManager.getApplication().invokeLater({
        if (round == detectRound) debuggerLabel.text = text
      }, ModalityState.any())
    }
  }

  private fun installDebugger() {
    DebuggerInstaller.install(null, reinstall.isSelected) { detectDebugger() }
  }

  private fun test() {
    val typed = pathField.text.trim()
    resultLabel.text = "Testing..."
    ProgressManager.getInstance().run(object : Task.Backgroundable(null, "Testing flxgdx", true) {
      private var message = ""

      override fun run(indicator: ProgressIndicator) {
        val exe = if (typed.isEmpty()) {
          service<FlxgdxLocator>().find()?.path
        } else {
          runCatching { Paths.get(typed) }.getOrNull()
        }
        message = if (exe == null) {
          "flxgdx was not found."
        } else {
          service<FlxgdxCli>().probe(exe, indicator).describe()
        }
      }

      override fun onFinished() {
        ApplicationManager.getApplication().invokeLater({ resultLabel.text = message }, ModalityState.any())
      }
    })
  }
}
