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
package org.flixelgdx.intellij.doctor

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeUtil
import org.flixelgdx.intellij.model.CheckStatus
import org.flixelgdx.intellij.model.DoctorCheck
import org.flixelgdx.intellij.model.DoctorResult
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * The content of the "FlixelGDX" tool window: the checks of `flxgdx doctor` as a tree, grouped by category, with a
 * toolbar to run again, fix automatically and switch the offline mode.
 *
 * The panel only draws what [DoctorController] holds; it never runs `flxgdx` itself. All of it lives on the EDT.
 */
class DoctorPanel(private val project: Project) : JPanel(BorderLayout()), Disposable {

  private val controller = project.service<DoctorController>()
  private val summary = JBLabel()
  private val tree = Tree(DefaultTreeModel(DefaultMutableTreeNode()))

  init {
    tree.isRootVisible = false
    tree.showsRootHandles = true
    tree.cellRenderer = CheckRenderer()
    tree.emptyText.text = "Run the doctor to check this computer and the project."

    summary.border = JBUI.Borders.empty(4, 8)
    val group = DefaultActionGroup().apply {
      add(RunAction(controller, fix = false))
      add(RunAction(controller, fix = true))
      add(OfflineAction(controller))
    }
    val toolbar = ActionManager.getInstance().createActionToolbar(PLACE, group, false)
    toolbar.targetComponent = this

    add(toolbar.component, BorderLayout.WEST)
    add(summary, BorderLayout.NORTH)
    add(JBScrollPane(tree), BorderLayout.CENTER)

    controller.addListener(this) { render() }
    render()
  }

  override fun dispose() {}

  private fun render() {
    val result = controller.result
    summary.text = when {
      controller.running -> "Running the doctor..."
      result == null -> "Not run yet."
      else -> "${result.count(CheckStatus.OK)} ok, ${result.count(CheckStatus.WARNING)} warnings, " +
        "${result.count(CheckStatus.ERROR)} errors"
    }
    val root = DefaultMutableTreeNode()
    if (result != null) fill(root, result)
    tree.model = DefaultTreeModel(root)
    TreeUtil.expandAll(tree)
  }

  private fun fill(root: DefaultMutableTreeNode, result: DoctorResult) {
    for ((category, checks) in result.byCategory()) {
      val node = DefaultMutableTreeNode(category.replaceFirstChar { it.uppercase() })
      for (check in checks) {
        val child = DefaultMutableTreeNode(check)
        if (check.fix.isNotEmpty()) child.add(DefaultMutableTreeNode(FixLine(check.fix)))
        node.add(child)
      }
      root.add(node)
    }
  }

  /** The text of a fix, shown below its check. */
  private data class FixLine(val text: String)

  private class CheckRenderer : ColoredTreeCellRenderer() {
    override fun customizeCellRenderer(
      tree: JTree,
      value: Any?,
      selected: Boolean,
      expanded: Boolean,
      leaf: Boolean,
      row: Int,
      hasFocus: Boolean,
    ) {
      when (val item = (value as? DefaultMutableTreeNode)?.userObject) {
        is String -> append(item, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
        is FixLine -> append("Fix: ${item.text}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
        is DoctorCheck -> {
          icon = when (item.status) {
            CheckStatus.OK -> AllIcons.General.InspectionsOK
            CheckStatus.WARNING -> AllIcons.General.Warning
            CheckStatus.ERROR -> AllIcons.General.Error
          }
          append(item.message)
          if (item.fixed) append("  (fixed)", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
        }
      }
    }
  }

  /** The Run Again and Fix Automatically buttons. */
  private class RunAction(private val controller: DoctorController, private val fix: Boolean) :
    AnAction(
      if (fix) "Fix Automatically" else "Run Again",
      if (fix) "Run flxgdx doctor --fix, then check again" else "Run flxgdx doctor again",
      if (fix) AllIcons.Actions.QuickfixBulb else AllIcons.Actions.Refresh,
    ),
    DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = !controller.running
    }

    override fun actionPerformed(e: AnActionEvent) {
      controller.run(fix)
    }
  }

  /** The Offline toggle: when on, the doctor skips the checks that need the network. */
  private class OfflineAction(private val controller: DoctorController) :
    ToggleAction("Offline", "Skip the checks that need the network", AllIcons.Actions.OfflineMode),
    DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun isSelected(e: AnActionEvent): Boolean = controller.offline

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      controller.offline = state
    }
  }

  private companion object {
    const val PLACE = "FlixelGDXDoctor"
  }
}

/** Opens the "FlixelGDX" tool window of [project], if it exists. */
fun showDoctorWindow(project: Project) {
  ToolWindowManager.getInstance(project).getToolWindow(DOCTOR_WINDOW_ID)?.activate(null)
}

/** The id of the "FlixelGDX" tool window, declared in `plugin.xml`. */
const val DOCTOR_WINDOW_ID = "FlixelGDX"
