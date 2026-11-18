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
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import org.flixelgdx.intellij.toml.CatalogLibrary
import java.awt.event.MouseEvent
import javax.swing.DefaultListSelectionModel
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.ListSelectionModel

/**
 * The dialog behind Remove Library: a list of the `[libraries]` entries of the version catalog.
 *
 * The entries of the framework itself are shown greyed out with a tooltip and cannot be selected, because a game
 * cannot build without them. After the person picks a library, the dialog asks once more before it closes.
 *
 * @param libraries the entries of the catalog.
 */
class RemoveLibraryDialog(private val project: Project, private val libraries: List<CatalogLibrary>) :
  DialogWrapper(project, true) {

  private val list = object : JBList<CatalogLibrary>(libraries) {
    override fun getToolTipText(event: MouseEvent): String? {
      val item = libraries.getOrNull(locationToIndex(event.point))
      return if (item != null && item.framework) FRAMEWORK_TIP else null
    }
  }

  /** The library the person chose, set when the dialog is accepted. */
  var selected: CatalogLibrary? = null
    private set

  init {
    title = "Remove Library"
    list.selectionModel = object : DefaultListSelectionModel() {
      override fun setSelectionInterval(index0: Int, index1: Int) {
        if (libraries.getOrNull(index1)?.framework != true) super.setSelectionInterval(index0, index1)
      }
    }
    list.selectionMode = ListSelectionModel.SINGLE_SELECTION
    list.cellRenderer = object : ColoredListCellRenderer<CatalogLibrary>() {
      override fun customizeCellRenderer(
        list: JList<out CatalogLibrary>,
        value: CatalogLibrary,
        index: Int,
        selected: Boolean,
        hasFocus: Boolean,
      ) {
        if (value.framework) {
          append(value.label, SimpleTextAttributes.GRAYED_ATTRIBUTES)
          append("  framework", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
        } else {
          append(value.label)
          val version = value.version.ifEmpty { value.versionRef.let { if (it.isEmpty()) "" else "version.ref $it" } }
          if (version.isNotEmpty()) append("  $version", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
        }
      }
    }
    list.setEmptyText("The version catalog lists no libraries.")
    init()
  }

  override fun createCenterPanel(): JComponent {
    val scroll = JBScrollPane(list)
    scroll.preferredSize = JBUI.size(PREFERRED_WIDTH, PREFERRED_HEIGHT)
    scroll.border = JBUI.Borders.customLine(JBColor.border())
    return scroll
  }

  override fun getPreferredFocusedComponent(): JComponent = list

  override fun doValidate(): ValidationInfo? =
    if (list.selectedValue == null) ValidationInfo("Pick a library to remove.", list) else null

  override fun doOKAction() {
    val item = list.selectedValue ?: return
    val sure = MessageDialogBuilder.yesNo("Remove Library", "Remove ${item.alias} from the project?")
      .yesText("Remove")
      .ask(project)
    if (!sure) return
    selected = item
    super.doOKAction()
  }

  private companion object {
    const val FRAMEWORK_TIP = "The framework itself cannot be removed."
    const val PREFERRED_WIDTH = 480
    const val PREFERRED_HEIGHT = 240
  }
}
