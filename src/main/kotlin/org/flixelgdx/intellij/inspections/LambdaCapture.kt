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

import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UastCallKind
import org.jetbrains.uast.resolveToUElement
import org.jetbrains.uast.visitor.AbstractUastVisitor

/**
 * Finds out whether a lambda captures something from the code around it.
 *
 * A lambda that uses nothing from outside is created once and reused by the runtime, so it costs nothing per frame.
 * A lambda that uses a local variable, a parameter, `this` or an instance member has to be created again each time,
 * which is garbage. This helper tells the two apart.
 */
internal object LambdaCapture {

  /** Returns true when [lambda] reads a local, a parameter, `this` or an instance member of the enclosing class. */
  fun captures(lambda: ULambdaExpression): Boolean {
    val scope = lambda.sourcePsi ?: return false
    var found = false
    lambda.body.accept(object : AbstractUastVisitor() {

      override fun visitThisExpression(node: UThisExpression): Boolean {
        found = true
        return true
      }

      override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression): Boolean {
        val parent = node.uastParent
        if (parent is UQualifiedReferenceExpression && parent.selector == node) return false
        if (parent is UCallExpression && parent.methodIdentifier == node) return false
        val target = node.resolveToUElement()
        if (target is ULocalVariable || target is UParameter) {
          val declaration = target.sourcePsi
          if (declaration != null && !PsiTreeUtil.isAncestor(scope, declaration, true)) found = true
        } else if (target is UVariable && !isStatic(target)) {
          found = true
        }
        return false
      }

      override fun visitCallExpression(node: UCallExpression): Boolean {
        if (node.receiver == null && node.kind == UastCallKind.METHOD_CALL) {
          val method = node.resolveToUElement() as? UMethod
          if (method != null && !method.isStatic && !method.isConstructor) found = true
        }
        return false
      }
    })
    return found
  }

  private fun isStatic(variable: UVariable): Boolean =
    (variable.javaPsi as? PsiModifierListOwner)?.hasModifierProperty(PsiModifier.STATIC) == true
}
