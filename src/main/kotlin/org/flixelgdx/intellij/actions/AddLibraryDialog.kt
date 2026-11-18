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

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import org.flixelgdx.intellij.toml.CatalogNames
import org.flixelgdx.intellij.toml.Coordinates
import javax.swing.JComponent
import javax.swing.event.DocumentEvent

/**
 * The dialog behind Add Library: Maven coordinates, the alias in the version catalog, and the bundle to add it to.
 *
 * The alias follows the artifact name until the person edits it. Gradle plugins that also need an `id("...")` line
 * in a module's `plugins {}` block can be put in the `buildsrc` bundle here, but the `id` line stays a manual step.
 *
 * @param bundles the bundles of the catalog, offered in the bundle list; the first one is selected.
 * @param taken the aliases that already exist, which the new one must not repeat.
 */
class AddLibraryDialog(
  project: Project,
  bundles: List<String>,
  private val taken: Set<String>,
) : DialogWrapper(project, true) {

  private val coordinatesField = JBTextField()
  private val aliasField = JBTextField()
  private val bundleBox = ComboBox(bundles.toTypedArray()).apply { isEditable = true }
  private var aliasEdited = false
  private var syncing = false

  /** The coordinates that were typed, set when the dialog is accepted. */
  var coordinates: Coordinates? = null
    private set

  /** The alias that was typed, set when the dialog is accepted. */
  var alias: String = ""
    private set

  /** The bundle that was chosen, set when the dialog is accepted. */
  var bundle: String = ""
    private set

  init {
    title = "Add Library"
    setOKButtonText("Add")
    coordinatesField.emptyText.text = "group:artifact:version"
    coordinatesField.document.addDocumentListener(listener { followArtifact() })
    aliasField.document.addDocumentListener(listener { if (!syncing) aliasEdited = true })
    init()
  }

  override fun createCenterPanel(): JComponent = panel {
    row("Coordinates:") {
      cell(coordinatesField).align(AlignX.FILL).columns(COLUMNS)
        .comment("The Maven coordinates, such as org.flixelgdx:flixelgdx-ui:0.7.2.")
    }
    row("Alias:") {
      cell(aliasField).align(AlignX.FILL)
        .comment("The name in gradle/libs.versions.toml. It follows the artifact until you change it.")
    }
    row("Bundle:") {
      cell(bundleBox)
        .comment(
          "The module that uses it: core for game code, a platform name for one platform, a name ending in " +
            "Processors for an annotation processor, or buildsrc for a Gradle plugin (then add its id(\"...\") " +
            "line to the module's plugins block yourself).",
          maxLineLength = COMMENT_WIDTH,
        )
    }
  }

  override fun getPreferredFocusedComponent(): JComponent = coordinatesField

  override fun doValidate(): ValidationInfo? {
    if (Coordinates.parse(coordinatesField.text) == null) {
      return ValidationInfo("Enter the coordinates as group:artifact:version.", coordinatesField)
    }
    val alias = aliasField.text.trim()
    CatalogNames.aliasProblem(alias)?.let { return ValidationInfo(it, aliasField) }
    if (alias in taken) return ValidationInfo("The catalog already has a library called $alias.", aliasField)
    if (bundleText().isEmpty()) return ValidationInfo("Pick a bundle.", bundleBox)
    return null
  }

  override fun doOKAction() {
    coordinates = Coordinates.parse(coordinatesField.text)
    alias = aliasField.text.trim()
    bundle = bundleText()
    super.doOKAction()
  }

  private fun bundleText(): String = (bundleBox.editor.item ?: bundleBox.selectedItem)?.toString().orEmpty().trim()

  private fun followArtifact() {
    if (aliasEdited) return
    val artifact = coordinatesField.text.split(':').getOrNull(1)?.trim().orEmpty()
    syncing = true
    aliasField.text = if (artifact.isEmpty()) "" else CatalogNames.aliasFor(artifact)
    syncing = false
  }

  private fun listener(action: () -> Unit) = object : DocumentAdapter() {
    override fun textChanged(e: DocumentEvent) = action()
  }

  private companion object {
    const val COLUMNS = 32
    const val COMMENT_WIDTH = 60
  }
}
