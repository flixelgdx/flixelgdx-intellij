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
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiFileFactory
import org.flixelgdx.intellij.game.FlixelNotifier
import org.flixelgdx.intellij.game.GameProject
import org.flixelgdx.intellij.runconfig.RunConfigSync
import org.flixelgdx.intellij.template.GameTemplateData
import org.flixelgdx.intellij.template.GameTemplates
import org.flixelgdx.intellij.template.ProjectWriter
import org.flixelgdx.intellij.toml.CatalogMerge
import org.flixelgdx.intellij.toml.DocumentEdits
import org.flixelgdx.intellij.toml.FileChange
import org.flixelgdx.intellij.toml.GameProjectToml
import org.flixelgdx.intellij.toml.GameSettings
import org.flixelgdx.intellij.toml.VersionCatalog
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlFileType
import java.nio.file.Files
import java.nio.file.Path

/**
 * Tools > FlixelGDX > Add Platform: offers the platforms that are not enabled yet, and turns the chosen one on.
 *
 * Turning a platform on takes three steps. The module folder is created from the platform's files in the templates
 * (files that already exist are kept). The framework entries the platform needs are copied into the version catalog
 * from a freshly rendered one ([CatalogMerge]). Finally the platform is added to `platforms` in `project.toml`, and
 * Gradle syncs.
 */
class AddPlatformAction : GameAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val dir = GameProject.baseDir(project) ?: return
    val notifier = service<FlixelNotifier>()
    val settings = DocumentEdits.readToml(project, dir.resolve(GameProjectToml.PATH)) { GameProjectToml(it).settings() }
    if (settings == null) {
      notifier.error(project, TITLE, "${GameProjectToml.PATH} was not found.")
      return
    }
    val versions = DocumentEdits.readToml(project, dir.resolve(VersionCatalog.PATH)) { file ->
      val catalog = VersionCatalog(file)
      catalog.version(VersionCatalog.FRAMEWORK_VERSION) to catalog.version(VersionCatalog.KTX_VERSION)
    }
    val flixel = versions?.first
    if (flixel == null) {
      notifier.error(project, TITLE, "The version catalog ${VersionCatalog.PATH} has no flixelgdx version.")
      return
    }
    val options = GameTemplates.PLATFORMS.filter { it !in settings.platforms }
    if (options.isEmpty()) {
      notifier.info(project, TITLE, "Every platform is already enabled.")
      return
    }
    val dialog = ChoiceDialog(project, "Add Platform", "Platform:", options, { it }, okText = "Add")
    if (!dialog.showAndGet()) return
    val platform = dialog.choice ?: return
    val data = templateData(settings, platform, flixel, versions.second ?: flixel, dir)
    ProgressManager.getInstance().run(object : Task.Backgroundable(project, TITLE, false) {
      private var catalogText = ""
      private var created = 0

      override fun run(indicator: ProgressIndicator) {
        val files = GameTemplates.render(data, GameTemplates.DEFAULT)
        catalogText = files.first { it.path == VersionCatalog.PATH }.text()
        created = ProjectWriter.write(dir, files.filter { it.path.startsWith("$platform/") }, overwrite = false).size
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(dir.resolve(platform))
          ?.let { VfsUtil.markDirtyAndRefresh(false, true, true, it) }
      }

      override fun onSuccess() {
        enable(project, dir, platform, catalogText)
        val folder = if (created == 0) {
          "The $platform folder was already there."
        } else {
          "Created $created files in $platform/."
        }
        notifier.info(project, TITLE, "Added $platform. $folder")
      }

      override fun onThrowable(error: Throwable) {
        notifier.error(project, TITLE, error.message ?: error.toString())
      }
    })
  }

  /** Adds the missing catalog entries and the platform itself, then syncs Gradle and the run configurations. */
  private fun enable(project: Project, dir: Path, platform: String, catalogText: String) {
    val changes = listOf(
      FileChange.toml(dir.resolve(VersionCatalog.PATH)) { current ->
        val wanted = PsiFileFactory.getInstance(project)
          .createFileFromText(VersionCatalog.PATH.substringAfterLast('/'), TomlFileType, catalogText) as TomlFile
        CatalogMerge.missing(current, wanted)
      },
      FileChange.toml(dir.resolve(GameProjectToml.PATH)) { GameProjectToml(it).addPlatform(platform) },
    )
    DocumentEdits.edit(project, TITLE, changes)
    GameProject.refresh(project)
    project.service<RunConfigSync>().sync()
  }

  private fun templateData(
    settings: GameSettings,
    platform: String,
    flixel: String,
    ktx: String,
    dir: Path,
  ): GameTemplateData = GameTemplateData.of(
    title = settings.title,
    company = settings.company,
    version = settings.version,
    pkg = settings.pkg,
    language = settings.language,
    platforms = settings.platforms + platform,
    frameworkVersion = flixel,
    ktxVersion = ktx,
    folder = dir.fileName?.toString().orEmpty(),
    width = settings.width,
    height = settings.height,
  )

  private companion object {
    const val TITLE = "Add Platform"
  }
}

/**
 * Tools > FlixelGDX > Remove Platform: takes a platform out of `platforms` in `project.toml`, and deletes its module
 * folder when the person asks for it. The catalog entries of the platform stay, since they do no harm and make adding
 * the platform back easy.
 */
class RemovePlatformAction : GameAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val dir = GameProject.baseDir(project) ?: return
    val notifier = service<FlixelNotifier>()
    val path = dir.resolve(GameProjectToml.PATH)
    val platforms = DocumentEdits.readToml(project, path) { GameProjectToml(it).platforms() }
    if (platforms == null) {
      notifier.error(project, TITLE, "${GameProjectToml.PATH} was not found.")
      return
    }
    if (platforms.size <= 1) {
      notifier.info(project, TITLE, "A game needs at least one platform, so the last one cannot be removed.")
      return
    }
    val dialog = ChoiceDialog(
      project,
      "Remove Platform",
      "Platform:",
      platforms,
      { it },
      checkbox = "Also delete the module folder (this cannot be undone)",
      okText = "Remove",
    )
    if (!dialog.showAndGet()) return
    val platform = dialog.choice ?: return
    DocumentEdits.edit(project, TITLE, listOf(FileChange.toml(path) { GameProjectToml(it).removePlatform(platform) }))
    if (dialog.checked) deleteFolder(project, dir.resolve(platform))
    notifier.info(project, TITLE, "Removed $platform.")
    GameProject.refresh(project)
    project.service<RunConfigSync>().sync()
  }

  private fun deleteFolder(project: Project, folder: Path) {
    ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Deleting ${folder.fileName}", false) {
      override fun run(indicator: ProgressIndicator) {
        if (Files.exists(folder)) NioFiles.deleteRecursively(folder)
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(folder.parent)
          ?.let { VfsUtil.markDirtyAndRefresh(false, true, true, it) }
      }

      override fun onThrowable(error: Throwable) {
        service<FlixelNotifier>().error(project, TITLE, error.message ?: error.toString())
      }
    })
  }

  private companion object {
    const val TITLE = "Remove Platform"
  }
}
