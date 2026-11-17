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
import org.flixelgdx.intellij.model.InstalledPackage
import java.awt.event.MouseEvent
import javax.swing.DefaultListSelectionModel
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.ListSelectionModel

/**
 * The dialog behind Remove Library or Plugin: a list of the installed packages.
 *
 * The package that holds the framework is shown greyed out with a tooltip, and it cannot be selected, because
 * `flxgdx` never removes the framework. After the person picks a package, the dialog asks once more before it closes.
 *
 * @param packages the installed packages, as read from `flxgdx info`.
 */
class RemovePackageDialog(private val project: Project, private val packages: List<InstalledPackage>) :
  DialogWrapper(project, true) {

  private val list = object : JBList<InstalledPackage>(packages) {
    override fun getToolTipText(event: MouseEvent): String? {
      val index = locationToIndex(event.point)
      val item = packages.getOrNull(index)
      return if (item != null && item.framework) FRAMEWORK_TIP else null
    }
  }

  /** The package the person chose, set when the dialog is accepted. */
  var selected: InstalledPackage? = null
    private set

  init {
    title = "Remove Library or Plugin"
    list.selectionModel = object : DefaultListSelectionModel() {
      override fun setSelectionInterval(index0: Int, index1: Int) {
        if (packages.getOrNull(index1)?.framework != true) super.setSelectionInterval(index0, index1)
      }
    }
    list.selectionMode = ListSelectionModel.SINGLE_SELECTION
    list.cellRenderer = object : ColoredListCellRenderer<InstalledPackage>() {
      override fun customizeCellRenderer(
        list: JList<out InstalledPackage>,
        value: InstalledPackage,
        index: Int,
        selected: Boolean,
        hasFocus: Boolean,
      ) {
        if (value.framework) {
          append(value.label, SimpleTextAttributes.GRAYED_ATTRIBUTES)
          append("  framework", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
        } else {
          append(value.label)
          append("  ${value.kind}", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
        }
      }
    }
    list.setEmptyText("No packages are installed.")
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
    if (list.selectedValue == null) ValidationInfo("Pick a package to remove.", list) else null

  override fun doOKAction() {
    val item = list.selectedValue ?: return
    val sure = MessageDialogBuilder.yesNo("Remove Package", "Remove ${item.label} from the project?")
      .yesText("Remove")
      .ask(project)
    if (!sure) return
    selected = item
    super.doOKAction()
  }

  private companion object {
    const val FRAMEWORK_TIP = "The framework itself cannot be removed."
    const val PREFERRED_WIDTH = 420
    const val PREFERRED_HEIGHT = 220
  }
}
