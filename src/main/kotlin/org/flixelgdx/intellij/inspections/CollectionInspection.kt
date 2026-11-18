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
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiTypeElement
import com.intellij.psi.PsiVariable
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UField
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UastCallKind
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

/**
 * Reports standard Java collections (`ArrayList`, `HashMap`, `HashSet` and the rest of `java.util`) in fields, locals,
 * parameters and `new` expressions, plus the Kotlin helpers such as `mutableListOf`.
 *
 * Java's collections create an iterator for every for-each loop and box every number stored in them. In a game that
 * runs sixty times a second that becomes a steady stream of garbage, so the framework ships its own collections in
 * `org.flixelgdx.collections`. The inspection id is `FlixelGdxCollection`.
 *
 * For a field or local variable that is initialized with a no-argument or capacity constructor the inspection offers
 * [ReplaceCollectionFix] in Java and [KotlinReplaceCollectionFix] in Kotlin (where `arrayListOf()` and the other
 * empty helpers count as such a constructor); every other case is only reported, because a different method name or
 * a different order guarantee means the code needs a human decision. Parameters of overriding methods are skipped,
 * since their types come from the method being overridden.
 */
class CollectionInspection : GameCodeInspection() {

  override val hints: Array<Class<out UElement>> = arrayOf(
    UField::class.java,
    ULocalVariable::class.java,
    UParameter::class.java,
    UCallExpression::class.java,
  )

  override fun createVisitor(holder: ProblemsHolder) = object : AbstractUastNonRecursiveVisitor() {

    private val reported = HashSet<PsiElement>()

    override fun visitField(node: UField): Boolean = checkVariable(node)

    override fun visitLocalVariable(node: ULocalVariable): Boolean = checkVariable(node)

    override fun visitParameter(node: UParameter): Boolean = checkVariable(node)

    override fun visitCallExpression(node: UCallExpression): Boolean {
      when (node.kind) {
        UastCallKind.CONSTRUCTOR_CALL -> checkConstructor(node)
        UastCallKind.METHOD_CALL -> checkFactory(node)
        else -> {}
      }
      return true
    }

    private fun checkVariable(node: UVariable): Boolean {
      val psi = typeElementOf(node) ?: return true
      val name = flaggedName(node) ?: return true
      val variable = psi.parent as? PsiVariable
      val plan = variable?.let { ReplaceCollectionFix.plan(it) }
      val fixes = when {
        plan != null -> arrayOf<LocalQuickFix>(ReplaceCollectionFix(plan.target.name))
        else -> kotlinFix(node)
      }
      report(psi, name, *fixes)
      return true
    }

    private fun checkConstructor(node: UCallExpression) {
      val cls = node.resolve()?.containingClass ?: node.classReference?.resolve() as? PsiClass ?: return
      val name = cls.qualifiedName ?: return
      if (name !in CollectionMapping.flagged) return
      val parent = node.uastParent as? UVariable
      if (parent != null && typeElementOf(parent) != null && flaggedName(parent) != null) return
      report(node.sourcePsi ?: return, name)
    }

    private fun checkFactory(node: UCallExpression) {
      val name = node.methodName ?: return
      val replacement = CollectionMapping.kotlinFactories[name] ?: return
      val psi = node.sourcePsi ?: return
      if (psi.language.id != "kotlin") return
      val owner = node.resolve()?.containingClass?.qualifiedName
      if (owner != null && !owner.startsWith("kotlin.collections.")) return
      val target = node.methodIdentifier?.sourcePsi ?: psi
      val fixes = (node.uastParent as? UVariable)?.let(::kotlinFix) ?: emptyArray()
      reportWith(target, "Kotlin's $name()", replacement, *fixes)
    }

    /** The Kotlin quick fix for [node], or no fix when [KotlinCollectionPlan] cannot plan one. */
    private fun kotlinFix(node: UVariable): Array<LocalQuickFix> {
      val plan = KotlinCollectionPlan.plan(node) ?: return emptyArray()
      return arrayOf(KotlinReplaceCollectionFix(plan))
    }

    private fun typeElementOf(node: UVariable): PsiElement? {
      val psi = node.typeReference?.sourcePsi ?: return null
      if (psi is PsiTypeElement && psi.isInferredType) return null
      return psi
    }

    private fun flaggedName(node: UVariable): String? {
      val name = ((node.type as? PsiClassType)?.resolve())?.qualifiedName ?: return null
      if (name !in CollectionMapping.flagged) return null
      val param = node.javaPsi as? PsiParameter
      val method = param?.declarationScope as? PsiMethod
      if (method != null && method.findSuperMethods(true).isNotEmpty()) return null
      return name
    }

    private fun report(psi: PsiElement, name: String, vararg fixes: LocalQuickFix) =
      reportWith(psi, name, CollectionMapping.suggestion(name), *fixes)

    private fun reportWith(psi: PsiElement, what: String, suggestion: String, vararg fixes: LocalQuickFix) {
      if (psi.containingFile != holder.file || !reported.add(psi)) return
      val shown = what.substringAfterLast('.')
      holder.registerProblem(
        psi,
        "Standard Java collection '$shown' creates garbage. Prefer $suggestion from org.flixelgdx.collections.",
        *fixes,
      )
    }
  }
}
