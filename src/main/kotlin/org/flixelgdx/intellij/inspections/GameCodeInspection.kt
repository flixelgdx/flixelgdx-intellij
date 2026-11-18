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
package org.flixelgdx.intellij.inspections

import com.intellij.codeInspection.AbstractBaseUastLocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.uast.UElement
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

/**
 * The base of every FlixelGDX inspection.
 *
 * It is built on UAST, the IDE's language-neutral view of code, so one implementation checks Java and Kotlin files.
 * It also applies the shared rule that the inspections only run in modules that use the framework (see
 * [FrameworkScope]).
 *
 * To add an inspection, extend this class, list the UAST element types you care about in [hints] (this keeps the
 * IDE from visiting every node) and build a visitor in [createVisitor].
 */
abstract class GameCodeInspection : AbstractBaseUastLocalInspectionTool() {

  /** The UAST element types [createVisitor] wants to see. */
  protected abstract val hints: Array<Class<out UElement>>

  /** Creates the visitor that reports problems to [holder]. */
  protected abstract fun createVisitor(holder: ProblemsHolder): AbstractUastNonRecursiveVisitor

  override fun isAvailableForFile(file: PsiFile): Boolean = FrameworkScope.isFrameworkFile(file)

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    if (!FrameworkScope.isFrameworkFile(holder.file)) return PsiElementVisitor.EMPTY_VISITOR
    return UastHintedVisitorAdapter.create(holder.file.language, createVisitor(holder), hints, true)
  }
}
