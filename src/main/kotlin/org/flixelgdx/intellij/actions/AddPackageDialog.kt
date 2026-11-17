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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.service
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import org.flixelgdx.intellij.cli.FlxgdxTasks
import org.flixelgdx.intellij.model.CommandArgs
import org.flixelgdx.intellij.model.InstalledPackage
import org.flixelgdx.intellij.model.ManifestInfo
import org.flixelgdx.intellij.settings.FlxgdxSettings
import java.nio.file.Files
import java.nio.file.Paths
import javax.swing.JComponent

/**
 * The dialog behind Add Library and Add Plugin.
 *
 * It has two modes. "From a repository" is the usual way: give the repository, and optionally a package and a release
 * tag, and `flxgdx` reads the manifest of the repository. "Manual" is for a dependency that has no manifest: give its
 * Maven coordinates and the modules it belongs to.
 *
 * `flxgdx` has no command that lists the packages or releases of a GitHub repository, so those two fields are free
 * text: empty means "the only package" and "the latest release". For a local folder, Look up reads its manifest and
 * offers the package names.
 *
 * When the dialog is accepted, [args] holds the finished command and the repository is added to the recent list.
 *
 * @param kind `library` or `plugin`.
 * @param modules the modules a manual dependency can go to: `core` and the platforms of the project.
 * @param recents the repositories used recently, offered in the repository field.
 */
class AddPackageDialog(
  private val project: Project,
  private val kind: String,
  modules: List<String>,
  recents: List<String>,
) : DialogWrapper(project, true) {

  private val fromRepo = AtomicBooleanProperty(true)
  private val manual = AtomicBooleanProperty(false)
  private val repoBox = repoCombo(recents)
  private val pkgBox = ComboBox<String>().apply { isEditable = true }
  private val versionField = JBTextField()
  private val groupField = JBTextField()
  private val artifactField = JBTextField()
  private val manualVersionField = JBTextField()
  private val aliasField = JBTextField()
  private val pluginIdField = JBTextField()
  private val scopeBox = ComboBox(CommandArgs.SCOPES.toTypedArray())
  private val moduleBoxes = modules.map { JBCheckBox(it, it == "core") }
  private val status = JBLabel()
  private val isPlugin = kind == InstalledPackage.KIND_PLUGIN

  /** The finished command, set when the dialog is accepted. */
  var args: List<String> = emptyList()
    private set

  init {
    title = if (isPlugin) "Add Plugin" else "Add Library"
    versionField.emptyText.text = "latest"
    (pkgBox.editor.editorComponent as? JBTextField)?.emptyText?.text = "the only package"
    manual.afterChange { fromRepo.set(!it) }
    fromRepo.afterChange { manual.set(!it) }
    init()
  }

  override fun createCenterPanel(): JComponent = panel {
    buttonsGroup {
      row {
        radioButton("From a repository").bindSelected(fromRepo)
        radioButton("Manual").bindSelected(manual)
      }
    }
    panel {
      row("Repository:") {
        cell(repoBox).align(AlignX.FILL)
          .comment("owner/repo, a GitHub URL, or a folder that holds a flxgdx.yml.")
      }
      row("Package:") {
        cell(pkgBox).align(AlignX.FILL)
        button("Look Up") { lookUp() }
      }
      row {
        cell(status)
      }
      row("Version:") {
        cell(versionField).align(AlignX.FILL)
          .comment("A release tag such as v1.2.0. Leave empty for the latest release.")
      }
    }.visibleIf(fromRepo)
    panel {
      if (isPlugin) {
        row("Plugin id:") { cell(pluginIdField).align(AlignX.FILL) }
      }
      row("Group:") { cell(groupField).align(AlignX.FILL) }
      row("Artifact:") { cell(artifactField).align(AlignX.FILL) }
      row("Version:") { cell(manualVersionField).align(AlignX.FILL) }
      row("Modules:") {
        for (box in moduleBoxes) cell(box)
      }
      if (!isPlugin) {
        row("Scope:") { cell(scopeBox) }
      }
      row("Alias:") {
        cell(aliasField).align(AlignX.FILL)
          .comment("The name in the version catalog. Leave empty to use the artifact name.")
      }
    }.visibleIf(manual)
  }

  override fun doValidate(): ValidationInfo? {
    if (fromRepo.get()) {
      if (repoBox.text().isEmpty()) return ValidationInfo("Enter a repository.", repoBox)
      return null
    }
    if (isPlugin && pluginIdField.text.isBlank()) return ValidationInfo("Enter the plugin id.", pluginIdField)
    if (groupField.text.isBlank()) return ValidationInfo("Enter the group.", groupField)
    if (artifactField.text.isBlank()) return ValidationInfo("Enter the artifact.", artifactField)
    if (manualVersionField.text.isBlank()) return ValidationInfo("Enter the version.", manualVersionField)
    if (moduleBoxes.none { it.isSelected }) return ValidationInfo("Pick at least one module.", moduleBoxes.first())
    return null
  }

  override fun doOKAction() {
    args = if (fromRepo.get()) {
      val repo = repoBox.text()
      service<FlxgdxSettings>().rememberRepo(repo)
      CommandArgs.addFromRepo(kind, repo, pkgBox.text(), versionField.text)
    } else {
      CommandArgs.addManual(
        kind = kind,
        group = groupField.text,
        artifact = artifactField.text,
        version = manualVersionField.text,
        modules = moduleBoxes.filter { it.isSelected }.map { it.text },
        scope = scopeBox.selectedItem?.toString() ?: "default",
        alias = aliasField.text,
        pluginId = pluginIdField.text,
      )
    }
    super.doOKAction()
  }

  private fun lookUp() {
    val text = repoBox.text()
    if (text.isEmpty()) {
      status.text = "Enter a repository first."
      return
    }
    val modality = ModalityState.stateForComponent(repoBox)
    ApplicationManager.getApplication().executeOnPooledThread {
      val local = runCatching { Files.isDirectory(Paths.get(text)) }.getOrDefault(false)
      ApplicationManager.getApplication().invokeLater({
        if (local) {
          readManifest(text)
        } else {
          status.text = "Packages can only be listed for a local folder. Leave the package empty to use the only one."
        }
      }, modality)
    }
  }

  private fun readManifest(path: String) {
    FlxgdxTasks.run(project, "Reading manifest", CommandArgs.manifestCheck(path), ProjectActions.baseDir(project)) {
      if (it.isSuccess) {
        val names = ManifestInfo.parse(it.data).names(kind)
        pkgBox.model = CollectionComboBoxModel(names)
        pkgBox.selectedItem = if (names.size == 1) names.first() else ""
        status.text = if (names.isEmpty()) "That manifest has no ${kind}s." else "Found ${names.size} package(s)."
      }
    }
  }
}
