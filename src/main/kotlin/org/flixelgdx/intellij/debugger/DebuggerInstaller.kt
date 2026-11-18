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
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.util.io.HttpRequests
import org.flixelgdx.intellij.game.Notifier
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Downloads and installs the FlixelGDX Debugger from its latest GitHub release, shown as a background task with a
 * progress bar that follows the download.
 *
 * The install is a safe sequence of [InstallProgress.STEPS] steps: find the latest release tag, download the file for
 * this computer, check its SHA-256 when the release lists one, unpack it next to the install folder and swap it in
 * ([DebuggerUnpacker]), and create the launchers ([DebuggerLaunchers]). A step that fails leaves the previous install
 * as it was.
 *
 * Example:
 * ```
 * DebuggerInstaller.install(project, force = false) { result -> println(result.summary()) }
 * ```
 */
object DebuggerInstaller {

  private const val TITLE = "Installing FlixelGDX Debugger"
  private const val TIMEOUT_MS = 30_000
  private const val BUFFER = 64 * 1024

  /**
   * Starts the install as a cancelable background task.
   *
   * @param project the project the task belongs to, or `null`.
   * @param force download and unpack again even when the latest release is already installed.
   * @param onDone called on the EDT after a successful run, with what was done.
   */
  fun install(project: Project?, force: Boolean, onDone: (InstallResult) -> Unit = {}) {
    ProgressManager.getInstance().run(object : Task.Backgroundable(project, TITLE, true) {
      private var result: InstallResult? = null

      override fun run(indicator: ProgressIndicator) {
        result = run(indicator, force, DebuggerLocator.environment())
      }

      override fun onSuccess() {
        val done = result ?: return
        service<Notifier>().info(project, TITLE, done.summary())
        onDone(done)
      }

      override fun onThrowable(error: Throwable) {
        service<Notifier>().error(project, TITLE, error.message ?: error.toString())
      }
    })
  }

  /** Shows a notification that says the debugger is missing, with an Install Debugger button. */
  fun offer(project: Project?) {
    val group = NotificationGroupManager.getInstance().getNotificationGroup(Notifier.GROUP_ID)
    val notification = group.createNotification(
      "FlixelGDX Debugger is not installed",
      "Install it from its latest release, or set its path under Settings > Tools > FlixelGDX.",
      NotificationType.INFORMATION,
    )
    notification.addAction(
      NotificationAction.createSimpleExpiring("Install Debugger") { install(project, force = false) },
    )
    notification.notify(project)
  }

  private fun run(indicator: ProgressIndicator, force: Boolean, env: DebuggerEnvironment): InstallResult {
    indicator.isIndeterminate = false
    val asset = DebuggerRelease.asset(System.getProperty("os.name"), System.getProperty("os.arch"))
      ?: throw IOException(
        "The FlixelGDX Debugger has no build for this computer (${System.getProperty("os.name")}, " +
          "${System.getProperty("os.arch")}). It is built for Linux and Windows on x64 and ARM64, and macOS on ARM64.",
      )
    val dir = DebuggerPaths.installDir(env)
    val exe = DebuggerPaths.executable(dir, env)
    val previous = installedVersion(dir, exe)

    step(indicator, 1, "Looking up the latest release")
    val tag = latestTag(indicator)
    if (!force && !previous.isNullOrEmpty() && previous == tag) {
      return InstallResult(InstallResult.UNCHANGED, previous, exe.toString())
    }

    step(indicator, 2, "Downloading $asset")
    val zip = Files.createTempFile("flixelgdx-debugger-", ".zip")
    try {
      download(DebuggerRelease.latestDownloadUrl(asset), zip, indicator)

      step(indicator, 3, "Checking the download")
      checksum(asset)?.let { expected ->
        val actual = sha256(zip)
        if (!actual.equals(expected, ignoreCase = true)) {
          throw IOException("The SHA-256 of $asset is $actual, but the release lists $expected.")
        }
      }

      step(indicator, 4, "Unpacking into $dir")
      indicator.checkCanceled()
      DebuggerUnpacker.install(zip, dir, exe.fileName.toString())
      if (tag != null) Files.writeString(dir.resolve(DebuggerPaths.VERSION_FILE), tag + "\n")
    } finally {
      Files.deleteIfExists(zip)
    }

    step(indicator, 5, "Creating launchers")
    val notes = DebuggerLaunchers.create(dir, exe, env)
    indicator.fraction = 1.0
    val action = when {
      previous == null -> InstallResult.INSTALLED
      tag != null && previous.isNotEmpty() && previous != tag -> InstallResult.UPGRADED
      else -> InstallResult.REINSTALLED
    }
    return InstallResult(action, tag.orEmpty(), exe.toString(), notes)
  }

  /** The tag of the install in [dir]: `null` when nothing is installed, empty when the tag is not known. */
  private fun installedVersion(dir: Path, exe: Path): String? {
    if (!Files.isRegularFile(exe)) return null
    return runCatching { Files.readString(dir.resolve(DebuggerPaths.VERSION_FILE)).trim() }.getOrDefault("")
  }

  /** Follows the "latest release" redirect and reads the tag from where it ends, or `null` when it cannot be seen. */
  private fun latestTag(indicator: ProgressIndicator): String? = runCatching {
    HttpRequests.request(DebuggerRelease.LATEST_URL)
      .productNameAsUserAgent()
      .connectTimeout(TIMEOUT_MS)
      .readTimeout(TIMEOUT_MS)
      .connect { request ->
        indicator.checkCanceled()
        DebuggerRelease.tagFrom(request.connection.url.toString())
      }
  }.getOrNull()

  private fun download(url: String, target: Path, indicator: ProgressIndicator) {
    HttpRequests.request(url)
      .productNameAsUserAgent()
      .connectTimeout(TIMEOUT_MS)
      .readTimeout(TIMEOUT_MS)
      .connect { request ->
        val size = request.connection.contentLengthLong
        request.inputStream.use { input ->
          Files.newOutputStream(target).use { output ->
            val buffer = ByteArray(BUFFER)
            var done = 0L
            while (true) {
              indicator.checkCanceled()
              val n = input.read(buffer)
              if (n < 0) break
              output.write(buffer, 0, n)
              done += n
              InstallProgress.fraction(2, InstallProgress.STEPS, done, size)?.let { indicator.fraction = it }
            }
          }
        }
      }
  }

  /** The SHA-256 the release lists for [asset], or `null` when the release has no checksums file or no line for it. */
  private fun checksum(asset: String): String? {
    val text = try {
      HttpRequests.request(DebuggerRelease.latestDownloadUrl(DebuggerRelease.CHECKSUMS))
        .productNameAsUserAgent()
        .connectTimeout(TIMEOUT_MS)
        .readTimeout(TIMEOUT_MS)
        .readString(null)
    } catch (e: HttpRequests.HttpStatusException) {
      return null
    }
    return DebuggerRelease.parseChecksums(text)[asset]
  }

  private fun sha256(file: Path): String {
    val digest = MessageDigest.getInstance("SHA-256")
    Files.newInputStream(file).use { input ->
      val buffer = ByteArray(BUFFER)
      while (true) {
        val n = input.read(buffer)
        if (n < 0) break
        digest.update(buffer, 0, n)
      }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
  }

  private fun step(indicator: ProgressIndicator, step: Int, text: String) {
    indicator.checkCanceled()
    indicator.text = text
    indicator.fraction = (step - 1).toDouble() / InstallProgress.STEPS
  }
}
