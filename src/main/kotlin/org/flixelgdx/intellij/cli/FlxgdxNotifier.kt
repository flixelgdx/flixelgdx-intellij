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
package org.flixelgdx.intellij.cli

import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import org.flixelgdx.intellij.settings.FlxgdxConfigurable
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Shows the plugin's notifications in the "FlixelGDX" notification group.
 *
 * The "flxgdx is missing or too old" notification is special: it is shown at most once per IDE session, so a user
 * who already knows about the problem is not nagged every time an action runs.
 */
@Service(Service.Level.APP)
class FlxgdxNotifier {

  private val cliProblemShown = AtomicBoolean(false)

  /**
   * Explains how to fix a missing or outdated `flxgdx`, unless this was already shown in this IDE session.
   *
   * Does nothing when [status] is [CliStatus.Ok].
   *
   * @return `true` when a notification was actually shown.
   */
  fun notifyCliProblem(project: Project?, status: CliStatus): Boolean {
    val text = when (status) {
      is CliStatus.Ok -> return false
      is CliStatus.Missing ->
        "FlixelGDX could not find the flxgdx command-line tool. Install flxgdx so it is on your PATH, " +
          "or set its location in the settings."
      is CliStatus.TooOld ->
        "flxgdx ${status.found} is too old. FlixelGDX needs version ${status.minimum} or newer. " +
          "Update flxgdx, or point the settings at a newer copy."
      is CliStatus.Broken ->
        "flxgdx could not be run: ${status.reason}. Check the path in the settings."
    }
    if (!cliProblemShown.compareAndSet(false, true)) return false

    val notification = group().createNotification("flxgdx is not ready", text, NotificationType.WARNING)
    notification.addAction(
      NotificationAction.createSimpleExpiring("Open Settings") {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, FlxgdxConfigurable::class.java)
      },
    )
    notification.addAction(
      NotificationAction.createSimpleExpiring("Learn More") { BrowserUtil.browse(LEARN_MORE_URL) },
    )
    notification.notify(project)
    return true
  }

  /** Shows a warning that a command reported but carried on from. */
  fun warning(project: Project?, title: String, message: String) {
    group().createNotification(title, message, NotificationType.WARNING).notify(project)
  }

  /** Shows an error that a command ended with. */
  fun error(project: Project?, title: String, message: String) {
    group().createNotification(title, message, NotificationType.ERROR).notify(project)
  }

  /** Shows a plain information message. */
  fun info(project: Project?, title: String, message: String) {
    group().createNotification(title, message, NotificationType.INFORMATION).notify(project)
  }

  private fun group() = NotificationGroupManager.getInstance().getNotificationGroup(GROUP_ID)

  companion object {
    /** The id of the notification group, declared in `plugin.xml`. */
    const val GROUP_ID = "FlixelGDX"

    /** Where the "Learn More" button leads. */
    const val LEARN_MORE_URL = "https://github.com/flixelgdx/flixelgdx-cli"

    fun getInstance(): FlxgdxNotifier = service()
  }
}
