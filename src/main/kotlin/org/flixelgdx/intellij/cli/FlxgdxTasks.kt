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

import com.google.gson.JsonObject
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import java.nio.file.Path

/** Helpers that run `flxgdx` as a visible background task in the IDE. */
object FlxgdxTasks {

  /**
   * Runs one `flxgdx` command under a cancelable background task.
   *
   * The progress bar follows the `progress` events of the command. Once it ends, warnings are shown in one
   * notification, and a failure is shown as an error notification (a cancel is silent). When `flxgdx` is missing or
   * too old, the "flxgdx is not ready" notification is shown instead (once per IDE session) and the command is not
   * started.
   *
   * @param project the project the task belongs to, or `null`.
   * @param title the title of the background task and of its notifications, such as "Adding library".
   * @param args the command and its flags, without `--json` and `--no-input`.
   * @param workDir the working directory of the command.
   * @param listener receives events on a background thread while the command runs.
   * @param reportError decides whether a failed result is shown as an error notification. Pass a function that returns
   * `false` for commands whose failure still carries a useful result, such as `doctor` finding an error.
   * @param onFinished called on the EDT with the result when the task ends, whatever the outcome.
   */
  fun run(
    project: Project?,
    title: String,
    args: List<String>,
    workDir: Path? = null,
    listener: CliEventListener? = null,
    reportError: (CliResult) -> Boolean = { true },
    onFinished: ((CliResult) -> Unit)? = null,
  ) {
    ProgressManager.getInstance().run(object : Task.Backgroundable(project, title, true) {
      private var result = CliResult(-1, startFailure = "The command did not run.")

      override fun run(indicator: ProgressIndicator) {
        indicator.isIndeterminate = true
        val status = service<FlxgdxCli>().status(indicator)
        val ok = status as? CliStatus.Ok
        if (ok == null) {
          service<FlxgdxNotifier>().notifyCliProblem(project, status)
          result = CliResult(-1, startFailure = status.describe())
          return
        }

        val progressListener = object : CliEventListener {
          override fun onProgress(step: Int, total: Int?, message: String) {
            indicator.text = message
            if (total != null && total > 0) {
              indicator.isIndeterminate = false
              indicator.fraction = (step - 1).coerceAtLeast(0).toDouble() / total
            }
            listener?.onProgress(step, total, message)
          }

          override fun onLog(message: String, data: JsonObject?) {
            listener?.onLog(message, data)
          }

          override fun onWarning(message: String) {
            listener?.onWarning(message)
          }

          override fun onResult(data: JsonObject?) {
            listener?.onResult(data)
          }

          override fun onError(code: String?, message: String) {
            listener?.onError(code, message)
          }
        }
        result = service<FlxgdxRunner>().run(args, workDir, indicator, progressListener, ok.path)

        val notifier = service<FlxgdxNotifier>()
        if (result.warnings.isNotEmpty()) notifier.warning(project, title, result.warnings.joinToString("\n"))
        if (!result.isSuccess && !result.cancelled && reportError(result)) {
          notifier.error(project, title, result.describe())
        }
      }

      override fun onFinished() {
        onFinished?.invoke(result)
      }
    })
  }
}
