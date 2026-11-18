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

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiPrimitiveType
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UObjectLiteralExpression
import org.jetbrains.uast.UPolyadicExpression
import org.jetbrains.uast.UThrowExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.UastCallKind
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor
import org.jetbrains.uast.visitor.AbstractUastVisitor

/**
 * Reports code that creates garbage inside the per-frame methods of a game.
 *
 * A game redraws about sixty times a second. Every object created in `update(float)` or `draw(...)` has to be
 * cleaned up by the garbage collector, and a collection pause shows up as a stutter, most visibly on phones. The
 * inspection looks at the bodies of methods that override the framework's per-frame methods (found by what they
 * override, not by name) and reports `new` objects, arrays, string concatenation, boxing of primitives into
 * collections, and lambdas or anonymous classes. The inspection id is `FlixelGdxAllocation`.
 *
 * For a local variable that is initialized with an object that does not depend on anything in the method, Java files
 * also get the quick fix [MoveToFieldFix].
 *
 * Code in a `throw` is skipped because it runs only when something already went wrong, and lambdas and anonymous
 * classes are reported as a whole instead of being searched inside. A Kotlin lambda passed to a function of the
 * Kotlin standard library (`forEach`, `let`, ...) is skipped too, because most of those functions are inlined by the
 * compiler and create no object.
 */
class AllocationInspection : GameCodeInspection() {

  override val hints: Array<Class<out UElement>> = arrayOf(UMethod::class.java)

  override fun createVisitor(holder: ProblemsHolder) = object : AbstractUastNonRecursiveVisitor() {

    override fun visitMethod(node: UMethod): Boolean {
      if (!isPerFrame(node.javaPsi)) return true
      node.uastBody?.accept(Finder(holder))
      return true
    }
  }

  private class Finder(private val holder: ProblemsHolder) : AbstractUastVisitor() {

    override fun visitThrowExpression(node: UThrowExpression): Boolean = true

    override fun visitObjectLiteralExpression(node: UObjectLiteralExpression): Boolean {
      report(node.sourcePsi, "an anonymous class")
      return true
    }

    override fun visitLambdaExpression(node: ULambdaExpression): Boolean {
      if (captures(node) && !isStdlibCall(node)) report(node.sourcePsi, "a lambda that captures variables")
      return true
    }

    override fun visitCallExpression(node: UCallExpression): Boolean {
      when (node.kind) {
        UastCallKind.CONSTRUCTOR_CALL -> reportCreation(node, "a new object")
        UastCallKind.NEW_ARRAY_WITH_DIMENSIONS, UastCallKind.NEW_ARRAY_WITH_INITIALIZER ->
          reportCreation(node, "a new array")
        UastCallKind.NESTED_ARRAY_INITIALIZER -> {
          val outer = node.uastParent as? UCallExpression
          if (outer == null || !outer.kind.isArrayCreation()) reportCreation(node, "a new array")
        }
        else -> checkBoxing(node)
      }
      return false
    }

    override fun visitPolyadicExpression(node: UPolyadicExpression): Boolean {
      checkConcat(node)
      return false
    }

    override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
      checkConcat(node)
      return false
    }

    private fun UastCallKind.isArrayCreation() =
      this == UastCallKind.NEW_ARRAY_WITH_DIMENSIONS ||
        this == UastCallKind.NEW_ARRAY_WITH_INITIALIZER ||
        this == UastCallKind.NESTED_ARRAY_INITIALIZER

    private fun reportCreation(node: UCallExpression, what: String) {
      val psi = node.sourcePsi
      val local = psi?.parent as? PsiLocalVariable
      if (local != null && MoveToFieldFix.canApply(local, psi)) {
        report(psi, what, MoveToFieldFix())
      } else {
        report(psi, what)
      }
    }

    private fun checkConcat(node: UPolyadicExpression) {
      val op = node.operator
      if (op != UastBinaryOperator.PLUS && op != UastBinaryOperator.PLUS_ASSIGN) return
      val isString = if (op == UastBinaryOperator.PLUS_ASSIGN) {
        (node as? UBinaryExpression)?.leftOperand?.getExpressionType()?.equalsToText("java.lang.String") == true
      } else {
        node.getExpressionType()?.equalsToText("java.lang.String") == true
      }
      if (!isString) return
      val parent = node.uastParent as? UPolyadicExpression
      val inChain = parent != null && parent.operator == UastBinaryOperator.PLUS
      if (!inChain && node.evaluate() == null) report(node.sourcePsi, "a string built with +")
    }

    private fun checkBoxing(node: UCallExpression) {
      val method = node.resolve() ?: return
      val owner = method.containingClass?.qualifiedName ?: return
      if (!owner.startsWith("java.util.") && !owner.startsWith("org.flixelgdx.collections.")) return
      val params = method.parameterList.parameters
      val last = params.size - 1
      val args = node.valueArguments
      for (i in args.indices) {
        val param = params.getOrNull(i) ?: params.getOrNull(last) ?: return
        if (args[i].getExpressionType() is PsiPrimitiveType && param.type !is PsiPrimitiveType) {
          report(args[i].sourcePsi, "a boxed primitive (a number converted to an object)")
        }
      }
    }

    private fun report(psi: PsiElement?, what: String, vararg fixes: LocalQuickFix) {
      if (psi == null || psi.containingFile != holder.file) return
      holder.registerProblem(psi, "Allocation in per-frame code: $what creates garbage every frame.", *fixes)
    }
  }

  companion object {

    private val perFrameOwners = setOf(
      "org.flixelgdx.functional.FlixelUpdatable",
      "org.flixelgdx.functional.FlixelDrawable",
      "org.flixelgdx.FlixelBasic",
    )

    /**
     * Returns true when [method] overrides the framework's `update(float)` or `draw(...)`.
     *
     * It follows the real super methods instead of trusting the name, so an unrelated method called `update` is not
     * treated as per-frame code. The whole chain is followed, because a game's `update` usually overrides one in
     * `FlixelState`, which overrides the one in a group class, which finally overrides the framework's own.
     */
    fun isPerFrame(method: PsiMethod): Boolean {
      if (method.name != "update" && method.name != "draw") return false
      return overridesPerFrame(method, HashSet())
    }

    /** Walks up the super methods of [method] until one belongs to a per-frame owner. [seen] stops loops. */
    private fun overridesPerFrame(method: PsiMethod, seen: MutableSet<PsiMethod>): Boolean {
      for (parent in method.findSuperMethods(true)) {
        if (!seen.add(parent)) continue
        if (parent.containingClass?.qualifiedName in perFrameOwners) return true
        if (overridesPerFrame(parent, seen)) return true
      }
      return false
    }

    private fun isStdlibCall(lambda: ULambdaExpression): Boolean {
      if (lambda.sourcePsi?.language?.id != "kotlin") return false
      val call = lambda.uastParent as? UCallExpression ?: return false
      val owner = call.resolve()?.containingClass?.qualifiedName ?: return false
      return owner.startsWith("kotlin.")
    }

    private fun captures(lambda: ULambdaExpression): Boolean = LambdaCapture.captures(lambda)
  }
}
