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
package org.flixelgdx.intellij.spike

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import java.awt.Dimension
import java.awt.event.ActionEvent
import javax.swing.Action
import javax.swing.JComponent

/**
 * A developer-only experiment that finds out whether the IDE's embedded browser (JCEF) offers WebGPU.
 *
 * It is not for players of the plugin. The action only shows up when the IDE runs with
 * `-Dflixelgdx.wgslSpike=true` (see `docs/wgsl-preview-spike.md`). It opens a small window with a page that reports
 * `navigator.gpu`, `requestAdapter()` and `requestDevice()`, plus a button that opens Chromium's own `chrome://gpu`
 * report.
 */
class WgslPreviewSpikeAction : AnAction(), DumbAware {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = System.getProperty(FLAG) == "true" && JBCefApp.isSupported()
  }

  override fun actionPerformed(e: AnActionEvent) {
    val stream = WgslPreviewSpikeAction::class.java.getResourceAsStream(PAGE) ?: return
    val html = stream.use { String(it.readAllBytes(), Charsets.UTF_8) }
    SpikeDialog(html).show()
  }

  private class SpikeDialog(private val html: String) : DialogWrapper(true) {

    private val browser = JBCefBrowser()

    init {
      title = "WebGPU Probe (FlixelGDX spike)"
      init()
      browser.loadHTML(html)
    }

    override fun createCenterPanel(): JComponent = browser.component.apply { preferredSize = Dimension(760, 460) }

    override fun createActions(): Array<Action> = arrayOf(
      object : DialogWrapperAction("Open chrome://gpu") {
        override fun doAction(e: ActionEvent?) {
          browser.loadURL("chrome://gpu")
        }
      },
      object : DialogWrapperAction("Run Again") {
        override fun doAction(e: ActionEvent?) {
          browser.loadHTML(html)
        }
      },
      okAction,
    )

    override fun dispose() {
      browser.dispose()
      super.dispose()
    }
  }

  private companion object {
    const val FLAG = "flixelgdx.wgslSpike"
    const val PAGE = "/wgsl-spike/webgpu-probe.html"
  }
}
