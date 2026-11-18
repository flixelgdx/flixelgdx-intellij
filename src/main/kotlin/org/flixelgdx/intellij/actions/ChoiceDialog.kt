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
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import javax.swing.JComponent

/**
 * A small dialog that asks the person to pick one item from a list, with an optional checkbox below it.
 *
 * It serves Add Platform and Remove Platform (with the "delete the folder" checkbox).
 *
 * @param T the type of the items.
 * @param title the title of the dialog.
 * @param label the text in front of the list.
 * @param items the items to choose from; must not be empty.
 * @param text how an item is shown.
 * @param checkbox the text of the checkbox, or `null` for no checkbox.
 * @param okText the text of the OK button.
 */
class ChoiceDialog<T : Any>(
  project: Project,
  title: String,
  private val label: String,
  items: List<T>,
  private val text: (T) -> String,
  private val checkbox: String? = null,
  okText: String = "OK",
) : DialogWrapper(project, true) {

  private val combo = ComboBox(CollectionComboBoxModel(items))
  private val box = JBCheckBox(checkbox.orEmpty())

  /** The item the person chose, set when the dialog is accepted. */
  var choice: T? = null
    private set

  /** Whether the checkbox was ticked when the dialog was accepted. Always `false` without a checkbox. */
  var checked: Boolean = false
    private set

  init {
    this.title = title
    setOKButtonText(okText)
    combo.renderer = textListCellRenderer<T> { text(it) }
    init()
  }

  override fun createCenterPanel(): JComponent = panel {
    row(label) { cell(combo) }
    if (checkbox != null) {
      row { cell(box) }
    }
  }

  override fun doOKAction() {
    choice = combo.item
    checked = checkbox != null && box.isSelected
    super.doOKAction()
  }
}
