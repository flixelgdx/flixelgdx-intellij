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
import com.intellij.psi.PsiDeclarationStatement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.util.PsiTreeUtil

/**
 * Quick fix for [AllocationInspection] that turns a local variable into a private field.
 *
 * Before:
 * ```java
 * public void update(float elapsed) {
 *   Vector2 tmp = new Vector2();
 * }
 * ```
 *
 * After:
 * ```java
 * private Vector2 tmp = new Vector2();
 *
 * public void update(float elapsed) {}
 * ```
 *
 * The object is now created once and shared by every frame, so make sure the code does not keep a reference to it
 * after the method ends. The fix is only offered for Java, and only when the initializer does not use any local
 * variable or parameter of the method, because those would not exist in a field initializer.
 */
class MoveToFieldFix : PsiUpdateModCommandQuickFix() {

  override fun getFamilyName(): String = "Move to a field"

  override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
    val local = element.parent as? PsiLocalVariable ?: return
    if (!canApply(local, element)) return
    val statement = local.parent as? PsiDeclarationStatement ?: return
    val method = PsiTreeUtil.getParentOfType(local, PsiMethod::class.java) ?: return
    val cls = method.containingClass ?: return
    val factory = JavaPsiFacade.getElementFactory(project)
    val field = factory.createField(local.name, local.type)
    field.modifierList?.apply {
      setModifierProperty(PsiModifier.PRIVATE, true)
      setModifierProperty(PsiModifier.STATIC, method.hasModifierProperty(PsiModifier.STATIC))
      setModifierProperty(PsiModifier.FINAL, local.hasModifierProperty(PsiModifier.FINAL))
    }
    field.initializer = element.copy() as? PsiExpression ?: return
    val lastField = cls.fields.lastOrNull()
    val added = if (lastField != null) {
      cls.addAfter(field, lastField)
    } else {
      cls.addBefore(field, method)
    }
    statement.delete()
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(added)
  }

  companion object {

    /**
     * Returns true when the fix can safely move [local], which is initialized with [initializer].
     *
     * The local must be the only variable of its statement, the class must not already have a field with that name,
     * and the initializer must not refer to any local variable or parameter that lives outside of it.
     */
    fun canApply(local: PsiLocalVariable, initializer: PsiElement): Boolean {
      if (local.initializer !== initializer || local.language.id != "JAVA") return false
      val statement = local.parent as? PsiDeclarationStatement ?: return false
      if (statement.declaredElements.size != 1) return false
      val method = PsiTreeUtil.getParentOfType(local, PsiMethod::class.java) ?: return false
      val cls = method.containingClass ?: return false
      if (cls.isInterface || cls.findFieldByName(local.name, true) != null) return false
      return PsiTreeUtil.findChildrenOfType(initializer, PsiReferenceExpression::class.java).none { ref ->
        val target = ref.resolve()
        (target is PsiLocalVariable || target is PsiParameter) && !PsiTreeUtil.isAncestor(initializer, target, true)
      }
    }
  }
}
