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
package org.flixelgdx.intellij.wgsl

import com.intellij.icons.AllIcons
import com.intellij.ide.actions.CreateFileFromTemplateAction
import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import org.flixelgdx.intellij.cli.FlxgdxProject

/**
 * File > New > WGSL Shader: creates a starter fragment shader from the `WGSL Shader` file template.
 *
 * The starter follows the framework's shader contract (see [WgslContract]). It is offered only in FlixelGDX
 * projects. Game shaders live in `src/main/shaders` of the core module, which is where you should create it.
 */
class NewWgslShaderAction :
  CreateFileFromTemplateAction({ "WGSL Shader" }, { "Create a FlixelGDX WGSL shader" }, AllIcons.FileTypes.Text),
  DumbAware {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun isAvailable(dataContext: DataContext): Boolean {
    val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return false
    return super.isAvailable(dataContext) && FlxgdxProject.isFlixelProject(project)
  }

  override fun buildDialog(project: Project, directory: PsiDirectory, builder: CreateFileFromTemplateDialog.Builder) {
    builder.setTitle("New WGSL Shader").addKind("WGSL shader", AllIcons.FileTypes.Text, WgslContract.TEMPLATE)
  }

  override fun getActionName(directory: PsiDirectory?, newName: String, templateName: String?): String =
    "Create WGSL Shader $newName"
}
