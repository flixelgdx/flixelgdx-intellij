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
package org.flixelgdx.intellij.game

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

/**
 * Shows the plugin's notifications in the "FlixelGDX" notification group.
 *
 * Example:
 * ```
 * service<Notifier>().info(project, "Add Library", "Added flixelgdx-ui to the core bundle.")
 * ```
 */
@Service(Service.Level.APP)
class Notifier {

  /** Shows an error that an action ended with. */
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
  }
}
