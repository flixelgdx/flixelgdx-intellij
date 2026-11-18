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
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UImportStatement
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

/**
 * Reports reflection (`java.lang.reflect`, `Class.forName`, `getDeclaredMethod`, `kotlin.reflect.full` and friends).
 *
 * Reflection finds code by name while the game runs. Platforms that compile the game ahead of time (the web build and
 * Android with R8) cannot see those names, so the code breaks only on the device. The inspection id is
 * `FlixelGdxReflection`.
 */
class ReflectionInspection : GameCodeInspection() {

  override val hints: Array<Class<out UElement>> = arrayOf(UCallExpression::class.java, UImportStatement::class.java)

  override fun createVisitor(holder: ProblemsHolder) = object : AbstractUastNonRecursiveVisitor() {

    override fun visitCallExpression(node: UCallExpression): Boolean {
      val method = node.resolve() ?: return true
      val owner = method.containingClass?.qualifiedName ?: return true
      if (!ReflectionRules.isReflection(owner, method.name)) return true
      val psi = node.methodIdentifier?.sourcePsi ?: node.sourcePsi ?: return true
      holder.registerProblem(psi, "Reflection ('${method.name}') breaks on the web and on Android with R8. $ADVICE")
      return true
    }

    override fun visitImportStatement(node: UImportStatement): Boolean {
      val psi = node.sourcePsi ?: return true
      if (psi.language.id != "kotlin") return true
      val path = psi.text.removePrefix("import").trim().substringBefore(" as ").trim()
      if (ReflectionRules.isReflectionImport(path)) {
        holder.registerProblem(psi, "Kotlin reflection ('$path') breaks on the web and on Android with R8. $ADVICE")
      }
      return true
    }
  }

  private companion object {
    const val ADVICE = "Write the code explicitly or use the framework's annotation processors."
  }
}
