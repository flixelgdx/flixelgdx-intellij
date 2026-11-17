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

import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiTypes
import com.intellij.psi.PsiVariable
import com.intellij.psi.codeStyle.JavaCodeStyleManager

/**
 * Quick fix for [FlixelCollectionInspection] that swaps a standard collection for the FlixelGDX one.
 *
 * Before:
 * ```java
 * private final Map<Integer, Enemy> enemies = new HashMap<>();
 * ```
 *
 * After:
 * ```java
 * private final FlixelIntMap<Enemy> enemies = new FlixelIntMap<>();
 * ```
 *
 * The fix only rewrites the declaration and the `new` expression. The methods of the FlixelGDX collections are named
 * differently (for example `getSize()` instead of `size()`), and the fix does not touch the code that uses the
 * variable, so the IDE will show errors there until you update the calls. That is why the fix is only offered for a
 * field or local variable that is initialized with a no-argument or a capacity constructor, which is the one place
 * where the replacement is certain. See [CollectionMapping] for the exact mapping.
 *
 * @property targetName the simple name of the FlixelGDX class, used for the menu text.
 */
class ReplaceCollectionFix(private val targetName: String) : PsiUpdateModCommandQuickFix() {

  override fun getName(): String = "Replace with $targetName"

  override fun getFamilyName(): String = "Replace with a FlixelGDX collection"

  override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
    val variable = element.parent as? PsiVariable ?: return
    val plan = plan(variable) ?: return
    val factory = JavaPsiFacade.getElementFactory(project)
    val typeElement = variable.typeElement ?: return
    val initializer = variable.initializer ?: return
    typeElement.replace(factory.createTypeElementFromText(plan.typeText, variable))
    initializer.replace(factory.createExpressionFromText(plan.newText, variable))
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(variable)
  }

  /**
   * The source text the fix writes.
   *
   * @property target the FlixelGDX class chosen by [CollectionMapping].
   * @property typeText the new declared type.
   * @property newText the new `new` expression.
   */
  class Plan(val target: Target, val typeText: String, val newText: String)

  companion object {

    /**
     * Works out the replacement for [variable], or returns null when the fix must not be offered.
     *
     * It must be a Java field or local variable declared with a plain generic type and initialized with
     * `new X<...>()` or `new X<...>(capacity)`, and the FlixelGDX class must be visible from the variable.
     */
    fun plan(variable: PsiVariable): Plan? {
      if ((variable !is PsiField && variable !is PsiLocalVariable) || variable.language.id != "JAVA") return null
      val typeElement = variable.typeElement ?: return null
      if (typeElement.isInferredType) return null
      val created = variable.initializer as? PsiNewExpression ?: return null
      if (created.anonymousClass != null || created.arrayDimensions.isNotEmpty()) return null
      val createdName = (created.classReference?.resolve() as? PsiClass)?.qualifiedName ?: return null
      val args = created.argumentList?.expressions ?: return null
      if (args.size > 1 || (args.size == 1 && args[0].type != PsiTypes.intType())) return null
      val type = typeElement.type as? PsiClassType ?: return null
      val declared = type.resolve()?.qualifiedName ?: return null
      val typeArgs = type.parameters
      if (typeArgs.any { it !is PsiClassType }) return null
      val names = typeArgs.map { (it as PsiClassType).resolve()?.qualifiedName }
      val target = CollectionMapping.target(declared, createdName, names) ?: return null
      val scope = variable.resolveScope
      if (JavaPsiFacade.getInstance(variable.project).findClass(target.qualifiedName, scope) == null) return null
      val kept = target.args.joinToString(", ") { typeArgs[it].getCanonicalText(false) }
      val generics = if (target.args.isEmpty()) "" else "<$kept>"
      val diamond = if (target.args.isEmpty()) "" else "<>"
      val argText = args.joinToString(", ") { it.text }
      return Plan(target, "${target.qualifiedName}$generics", "new ${target.qualifiedName}$diamond($argText)")
    }
  }
}
