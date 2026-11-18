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
import org.flixelgdx.intellij.game.FlixelNotifier
import org.flixelgdx.intellij.game.GameProject
import org.flixelgdx.intellij.maven.MavenCentral
import org.flixelgdx.intellij.toml.DocumentEdits
import org.flixelgdx.intellij.toml.FileChange
import org.flixelgdx.intellij.toml.SettingsPluginVersion
import org.flixelgdx.intellij.toml.VersionCatalog
import java.nio.file.Path

/** Helpers shared by the library actions. */
private object Catalog {

  /** The path of the version catalog of [project], or `null` for a project without a base folder. */
  fun path(project: Project): Path? = GameProject.baseDir(project)?.resolve(VersionCatalog.PATH)

  /** Reads the catalog of [project] with [read], or shows an error and returns `null` when it cannot be read. */
  fun <T> read(project: Project, title: String, read: (VersionCatalog) -> T): T? {
    val path = path(project) ?: return null
    val result = DocumentEdits.readToml(project, path) { read(VersionCatalog(it)) }
    if (result == null) {
      service<FlixelNotifier>().error(project, title, "The version catalog ${VersionCatalog.PATH} was not found.")
    }
    return result
  }
}

/**
 * Tools > FlixelGDX > Add Library: adds Maven coordinates to the version catalog and to the bundle of a module, then
 * syncs Gradle.
 */
class AddLibraryAction : FlxgdxAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val path = Catalog.path(project) ?: return
    val (bundles, taken) = Catalog.read(project, TITLE) { catalog ->
      val names = catalog.bundles().ifEmpty { VersionCatalog.STANDARD_BUNDLES }
      names to catalog.libraries().map { it.alias }.toSet()
    } ?: return
    val dialog = AddLibraryDialog(project, bundles, taken)
    if (!dialog.showAndGet()) return
    val coordinates = dialog.coordinates ?: return
    val changed = DocumentEdits.edit(
      project,
      TITLE,
      listOf(FileChange.toml(path) { VersionCatalog(it).addLibrary(dialog.alias, coordinates, dialog.bundle) }),
    )
    if (!changed) return
    val lines = arrayListOf("Added $coordinates as ${dialog.alias} to the ${dialog.bundle} bundle.")
    if (dialog.bundle == BUILDSRC) {
      lines += "If it is a Gradle plugin, add its id(\"...\") line to the plugins block of the module that uses it."
    }
    service<FlixelNotifier>().info(project, TITLE, lines.joinToString("\n"))
    GameProject.refresh(project)
  }

  private companion object {
    const val TITLE = "Add Library"
    const val BUILDSRC = "buildsrc"
  }
}

/** Tools > FlixelGDX > Remove Library: lists the catalog's libraries and removes the one that is picked. */
class RemoveLibraryAction : FlxgdxAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val path = Catalog.path(project) ?: return
    val libraries = Catalog.read(project, TITLE) { it.libraries() } ?: return
    val dialog = RemoveLibraryDialog(project, libraries)
    if (!dialog.showAndGet()) return
    val library = dialog.selected ?: return
    val changed = DocumentEdits.edit(
      project,
      TITLE,
      listOf(FileChange.toml(path) { VersionCatalog(it).removeLibrary(library.alias) }),
    )
    if (!changed) return
    service<FlixelNotifier>().info(project, TITLE, "Removed ${library.alias} from the version catalog.")
    GameProject.refresh(project)
  }

  private companion object {
    const val TITLE = "Remove Library"
  }
}

/**
 * Tools > FlixelGDX > Upgrade: looks up the latest framework release on Maven Central, shows it next to the version
 * the game uses, and after the person confirms, changes `[versions]` in the catalog and the settings plugin version in
 * `settings.gradle.kts`. A Kotlin game's extension version is offered too.
 */
class UpgradeAction : FlxgdxAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val current = Catalog.read(project, TITLE) { catalog ->
      listOfNotNull(
        catalog.version(VersionCatalog.FLIXEL_VERSION)?.let { Triple("FlixelGDX", VersionCatalog.FLIXEL_VERSION, it) },
        catalog.version(VersionCatalog.KTX_VERSION)?.let { Triple("Kotlin extension", VersionCatalog.KTX_VERSION, it) },
      )
    } ?: return
    if (current.isEmpty()) {
      service<FlixelNotifier>().error(project, TITLE, "The version catalog has no flixelgdx version to upgrade.")
      return
    }
    ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Looking for FlixelGDX upgrades", true) {
      private var choices = emptyList<VersionChoice>()

      override fun run(indicator: ProgressIndicator) {
        choices = current.map { (label, key, version) ->
          val artifact = if (key == VersionCatalog.KTX_VERSION) MavenCentral.KTX_CORE else MavenCentral.FLIXEL_CORE
          val latest = runCatching { MavenCentral.latest(MavenCentral.FLIXEL_GROUP, artifact, indicator) }.getOrNull()
          VersionChoice(label, key, version, latest)
        }
      }

      override fun onSuccess() {
        review(project, choices)
      }
    })
  }

  private fun review(project: Project, choices: List<VersionChoice>) {
    val notifier = service<FlixelNotifier>()
    if (choices.all { it.latest == it.current }) {
      notifier.info(project, TITLE, "FlixelGDX is up to date.")
      return
    }
    val dialog = UpgradeDialog(project, choices)
    if (!dialog.showAndGet()) return
    val dir = GameProject.baseDir(project) ?: return
    val chosen = dialog.chosen
    val flixel = chosen[VersionCatalog.FLIXEL_VERSION]
    val changes = arrayListOf(
      FileChange.toml(dir.resolve(VersionCatalog.PATH)) { file ->
        val catalog = VersionCatalog(file)
        chosen.flatMap { (key, version) -> catalog.setVersion(key, version) }
      },
    )
    if (flixel != null) {
      changes += FileChange.text(dir.resolve(SettingsPluginVersion.PATH)) { text ->
        listOfNotNull(SettingsPluginVersion.edit(text, flixel))
      }
    }
    if (!DocumentEdits.edit(project, TITLE, changes)) {
      notifier.info(project, TITLE, "Nothing changed.")
      return
    }
    val summary = choices.joinToString("\n") { "${it.label}: ${it.current} to ${chosen[it.key]}" }
    notifier.info(project, TITLE, summary)
    GameProject.refresh(project)
  }

  private companion object {
    const val TITLE = "Upgrade FlixelGDX"
  }
}
