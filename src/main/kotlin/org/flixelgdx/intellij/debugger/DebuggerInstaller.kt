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
package org.flixelgdx.intellij.debugger

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.flixelgdx.intellij.cli.FlxgdxNotifier
import org.flixelgdx.intellij.cli.FlxgdxTasks
import org.flixelgdx.intellij.model.CommandArgs

/**
 * Installs the FlixelGDX Debugger with `flxgdx install debugger`, shown as a background task with a progress bar that
 * follows the download.
 *
 * Like every other command it goes through [FlxgdxTasks], so a missing `flxgdx`, failures and cancellation are
 * handled the same way as everywhere else in the plugin.
 */
object DebuggerInstaller {

  private const val TITLE = "Installing FlixelGDX Debugger"

  /**
   * Starts the install.
   *
   * @param project the project the task belongs to, or `null`.
   * @param force download and unpack again even when the debugger is already installed (`--force`).
   * @param onDone called on the EDT after a successful run, with what `flxgdx` reported.
   */
  fun install(project: Project?, force: Boolean, onDone: (InstallResult) -> Unit = {}) {
    FlxgdxTasks.run(
      project,
      TITLE,
      CommandArgs.installDebugger(force),
      logFraction = InstallProgress::fraction,
    ) { result ->
      if (result.isSuccess) {
        val done = InstallResult.parse(result.data)
        service<FlxgdxNotifier>().info(project, TITLE, done.summary())
        onDone(done)
      }
    }
  }

  /** Shows a notification that says the debugger is missing, with an Install Debugger button. */
  fun offer(project: Project?) {
    val group = NotificationGroupManager.getInstance().getNotificationGroup(FlxgdxNotifier.GROUP_ID)
    val notification = group.createNotification(
      "FlixelGDX Debugger is not installed",
      "Install it with flxgdx, or set its path under Settings > Tools > FlixelGDX.",
      NotificationType.INFORMATION,
    )
    notification.addAction(
      NotificationAction.createSimpleExpiring("Install Debugger") { install(project, force = false) },
    )
    notification.notify(project)
  }
}
