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
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.panel
import org.flixelgdx.intellij.debugger.DebuggerInstaller
import org.flixelgdx.intellij.debugger.DebuggerLauncher
import org.flixelgdx.intellij.debugger.LaunchOutcome
import org.flixelgdx.intellij.game.Notifier
import javax.swing.JComponent

/** Asks whether the debugger should be downloaded again even when it is already installed. */
class InstallDebuggerDialog(project: Project?) : DialogWrapper(project, true) {

  private val force = JBCheckBox("Reinstall even if it is already installed")

  /** Whether the reinstall box was ticked when the dialog was accepted. */
  var reinstall: Boolean = false
    private set

  init {
    title = "Install FlixelGDX Debugger"
    setOKButtonText("Install")
    init()
  }

  override fun createCenterPanel(): JComponent = panel {
    row { label("Downloads the latest FlixelGDX Debugger release from GitHub.") }
    row { cell(force) }
  }

  override fun doOKAction() {
    reinstall = force.isSelected
    super.doOKAction()
  }
}

/** Tools > FlixelGDX > Install Debugger: downloads and installs the latest debugger release with progress. */
class InstallDebuggerAction : GameAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val dialog = InstallDebuggerDialog(project)
    if (!dialog.showAndGet()) return
    DebuggerInstaller.install(project, dialog.reinstall)
  }
}

/**
 * Tools > FlixelGDX > Open Debugger: starts the FlixelGDX Debugger as its own program. When it is not installed, a
 * notification offers to install it.
 */
class OpenDebuggerAction : GameAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Opening FlixelGDX Debugger", false) {
      private var outcome: LaunchOutcome = LaunchOutcome.NotInstalled

      override fun run(indicator: ProgressIndicator) {
        outcome = DebuggerLauncher.ensureRunning()
      }

      override fun onSuccess() {
        report(project, outcome)
      }
    })
  }

  private fun report(project: Project, outcome: LaunchOutcome) {
    val notifier = service<Notifier>()
    when (outcome) {
      is LaunchOutcome.NotInstalled -> DebuggerInstaller.offer(project)
      is LaunchOutcome.AlreadyRunning -> notifier.info(project, TITLE, "The debugger is already running.")
      is LaunchOutcome.Started -> {}
      is LaunchOutcome.Failed ->
        notifier.error(project, TITLE, "The debugger at ${outcome.path} could not be started: ${outcome.reason}")
    }
  }

  private companion object {
    const val TITLE = "FlixelGDX Debugger"
  }
}
