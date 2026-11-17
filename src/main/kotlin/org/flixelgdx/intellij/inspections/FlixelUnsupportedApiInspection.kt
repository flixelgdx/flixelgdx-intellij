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

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiImportStatementBase
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UImportStatement
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UTypeReferenceExpression
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

/**
 * Reports library APIs that do not exist on Android or the web (see [UnsupportedApis] for the list).
 *
 * Android's runtime only has the Java 17 library and the web build only has what TeaVM provides, so a call that
 * works on a desktop JDK 25 can crash the game on a phone or in a browser. The inspection id is
 * `FlixelGdxUnsupportedApi`.
 */
class FlixelUnsupportedApiInspection : FlixelInspection() {

  override val hints: Array<Class<out UElement>> = arrayOf(
    UCallExpression::class.java,
    UTypeReferenceExpression::class.java,
    USimpleNameReferenceExpression::class.java,
  )

  override fun createVisitor(holder: ProblemsHolder) = object : AbstractUastNonRecursiveVisitor() {

    private val reported = HashSet<PsiElement>()

    override fun visitCallExpression(node: UCallExpression): Boolean {
      val method = node.resolve() ?: return true
      val owner = method.containingClass?.qualifiedName ?: return true
      val rule = UnsupportedApis.forMethod(owner, method.name) ?: return true
      val psi = node.methodIdentifier?.sourcePsi ?: node.sourcePsi ?: return true
      report(psi, UnsupportedApis.message(rule, "${method.containingClass?.name}.${method.name}", method.name))
      return true
    }

    override fun visitTypeReferenceExpression(node: UTypeReferenceExpression): Boolean {
      val cls = (node.type as? PsiClassType)?.resolve() ?: return true
      checkClass(node.sourcePsi, cls, node)
      return true
    }

    override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression): Boolean {
      val cls = node.resolve() as? PsiClass ?: return true
      checkClass(node.sourcePsi, cls, node)
      return true
    }

    private fun checkClass(psi: PsiElement?, cls: PsiClass, node: UElement) {
      psi ?: return
      if (isImport(psi, node)) return
      val name = cls.qualifiedName ?: return
      val rule = UnsupportedApis.forClass(name) ?: return
      report(psi, UnsupportedApis.message(rule, cls.name ?: name))
    }

    private fun isImport(psi: PsiElement, node: UElement): Boolean {
      if (PsiTreeUtil.getParentOfType(psi, PsiImportStatementBase::class.java) != null) return true
      return generateSequence(node.uastParent) { it.uastParent }.any { it is UImportStatement }
    }

    private fun report(psi: PsiElement, message: String) {
      if (psi.containingFile != holder.file) return
      if (reported.any { PsiTreeUtil.isAncestor(it, psi, false) || PsiTreeUtil.isAncestor(psi, it, false) }) return
      reported.add(psi)
      holder.registerProblem(psi, message)
    }
  }
}
