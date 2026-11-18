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
package org.flixelgdx.intellij.wizard

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.flixelgdx.intellij.game.Notifier
import org.flixelgdx.intellij.maven.MavenCentral
import org.flixelgdx.intellij.runconfig.RunConfigSync
import org.flixelgdx.intellij.template.GameLanguage
import org.flixelgdx.intellij.template.GameTemplates
import org.flixelgdx.intellij.template.ProjectWriter
import org.jetbrains.plugins.gradle.service.project.open.linkAndSyncGradleProject
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Creates the files of a FlixelGDX game inside the project the wizard just opened, then imports it as a Gradle
 * project.
 *
 * Think of it as a builder who arrives at an empty plot with a prefabricated house: [create] first finds out which
 * framework version to use, assembles every file in memory from the templates, and only then puts them all on the
 * plot at once. If anything goes wrong while writing, the files are taken away again. Once the house stands, the
 * Gradle import starts so the IDE learns about the modules.
 */
@Service(Service.Level.PROJECT)
class NewProjectRunner(private val project: Project, private val scope: CoroutineScope) {

  /**
   * Creates the game described by [spec] in a cancelable background task, then imports it.
   *
   * The framework version is the one typed in the wizard, or else the latest release on Maven Central. A Kotlin game
   * also gets the latest release of the Kotlin extension; when that lookup fails, it uses the framework version, since
   * the two are released together. Failures are shown as an error notification and leave the folder as it was.
   *
   * Call it from any thread; it only schedules work.
   */
  fun create(spec: NewProjectSpec) {
    ProgressManager.getInstance().run(object : Task.Backgroundable(project, TITLE, true) {
      override fun run(indicator: ProgressIndicator) {
        indicator.isIndeterminate = false
        indicator.fraction = 0.0
        if (TargetFolder.hasForeignFiles(listEntries(spec.dir))) {
          throw IOException("The folder ${spec.dir} is not empty. Pick an empty folder or another name.")
        }
        indicator.text = "Finding the FlixelGDX version"
        val flixel = spec.typedFrameworkVersion ?: latest(MavenCentral.FRAMEWORK_CORE, indicator)
        val ktx = if (spec.language == GameLanguage.KOTLIN) {
          runCatching { MavenCentral.latest(MavenCentral.FRAMEWORK_GROUP, MavenCentral.KTX_CORE, indicator) }
            .getOrDefault(flixel)
        } else {
          flixel
        }
        indicator.checkCanceled()
        indicator.fraction = 0.5
        indicator.text = "Writing the project files"
        val files = GameTemplates.render(spec.templateData(flixel, ktx), spec.template)
        ProjectWriter.write(spec.dir, files)
        indicator.fraction = 1.0
      }

      override fun onSuccess() {
        importGradle(spec.dir)
        project.service<RunConfigSync>().sync()
      }

      override fun onThrowable(error: Throwable) {
        service<Notifier>().error(project, TITLE, error.message ?: error.toString())
      }
    })
  }

  private fun latest(artifact: String, indicator: ProgressIndicator): String =
    try {
      MavenCentral.latest(MavenCentral.FRAMEWORK_GROUP, artifact, indicator)
    } catch (e: IOException) {
      throw IOException(
        "Could not find the latest FlixelGDX version on Maven Central (${e.message}). " +
          "Check your connection, or type a version under Advanced in the wizard.",
        e,
      )
    }

  private fun listEntries(dir: Path): List<String> {
    if (!Files.isDirectory(dir)) return emptyList()
    return Files.list(dir).use { s -> s.map { it.fileName.toString() }.toList() }
  }

  private fun importGradle(dir: Path) {
    scope.launch {
      withContext(Dispatchers.IO) {
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(dir)
          ?.let { VfsUtil.markDirtyAndRefresh(false, true, true, it) }
      }
      linkAndSyncGradleProject(project, dir.toString())
    }
  }

  companion object {
    private const val TITLE = "Creating FlixelGDX project"
  }
}
