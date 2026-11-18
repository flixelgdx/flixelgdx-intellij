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
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiTypes
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UField
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UastCallKind
import org.jetbrains.uast.toUElement

/**
 * Quick fix for [FlixelCollectionInspection] that swaps a standard collection for the FlixelGDX one in Kotlin code.
 *
 * Before:
 * ```kotlin
 * private val enemies: ArrayList<Enemy> = arrayListOf()
 * ```
 *
 * After:
 * ```kotlin
 * private val enemies: FlixelArray<Enemy> = FlixelArray()
 * ```
 *
 * It is the Kotlin sibling of [ReplaceCollectionFix] and follows the same rules (see [KotlinCollectionPlan]). The
 * decision is made while the inspection runs, where the IDE can resolve types, and this fix only writes the planned
 * text. That keeps the plugin free of a dependency on the Kotlin plugin: the fix finds the declared type and the
 * initializer through UAST, which only needs the syntax, and replaces their text in the document. The code that uses
 * the variable is not changed, since the FlixelGDX collections name their methods differently.
 *
 * @property plan the replacement worked out by [KotlinCollectionPlan.plan].
 */
class KotlinReplaceCollectionFix(private val plan: KotlinCollectionPlan.Plan) : PsiUpdateModCommandQuickFix() {

  override fun getName(): String = "Replace with ${plan.target.name}"

  override fun getFamilyName(): String = "Replace with a FlixelGDX collection"

  override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
    val variable = variableOf(element) ?: return
    val init = variable.uastInitializer?.sourcePsi ?: return
    val typeRef = variable.typeReference?.sourcePsi
    val document = updater.document
    val edits = ArrayList<Triple<Int, Int, String>>()
    edits += Triple(init.textRange.startOffset, init.textRange.endOffset, plan.initializerText)
    val typeText = plan.typeText
    if (typeText != null && typeRef != null) {
      edits += Triple(typeRef.textRange.startOffset, typeRef.textRange.endOffset, typeText)
    }
    importEdit(document.text)?.let(edits::add)
    for ((start, end, text) in edits.sortedByDescending { it.first }) document.replaceString(start, end, text)
    PsiDocumentManager.getInstance(project).commitDocument(document)
  }

  /** Plans the import of the FlixelGDX class, or returns null when the file already has it. */
  private fun importEdit(text: String): Triple<Int, Int, String>? {
    val name = plan.target.qualifiedName
    val lines = text.lines()
    if (lines.any { it.trim() == "import $name" || it.trim() == "import ${CollectionMapping.PACKAGE}.*" }) return null
    var offset = 0
    var afterImport = -1
    var afterPackage = -1
    for (line in lines) {
      val end = offset + line.length
      val trimmed = line.trim()
      if (trimmed.startsWith("import ")) afterImport = end
      if (trimmed.startsWith("package ")) afterPackage = end
      offset = end + 1
    }
    return when {
      afterImport >= 0 -> Triple(afterImport, afterImport, "\nimport $name")
      afterPackage >= 0 -> Triple(afterPackage, afterPackage, "\n\nimport $name")
      else -> Triple(0, 0, "import $name\n\n")
    }
  }

  private fun variableOf(element: PsiElement): UVariable? {
    var node: UElement? = element.toUElement()
    while (node != null && node !is UVariable) node = node.uastParent
    return node as? UVariable
  }
}

/**
 * Works out whether [KotlinReplaceCollectionFix] can rewrite a Kotlin property or local variable, and what to write.
 *
 * The rules match the Java fix: the variable is initialized with a no-argument or capacity constructor of
 * `ArrayList`, `HashSet` or `HashMap`, or with one of the Kotlin helpers that create those (`arrayListOf()`,
 * `mutableListOf()`, `hashSetOf()`, `hashMapOf()`) called without elements. `mutableSetOf()` and `mutableMapOf()`
 * are left alone, because they keep insertion order and the FlixelGDX collections do not. The type arguments must
 * be plain classes, so the replacement is certain. [CollectionMapping] picks the FlixelGDX class.
 */
object KotlinCollectionPlan {

  /** The Kotlin helpers the fix understands, and the Java class each one creates. */
  private val factories = mapOf(
    "arrayListOf" to "java.util.ArrayList",
    "mutableListOf" to "java.util.ArrayList",
    "hashSetOf" to "java.util.HashSet",
    "hashMapOf" to "java.util.HashMap",
  )

  /**
   * The text the fix writes.
   *
   * @property target the FlixelGDX class chosen by [CollectionMapping].
   * @property typeText the new declared type, or null when the variable has no written type.
   * @property initializerText the new initializer, such as `FlixelArray()`.
   */
  class Plan(val target: Target, val typeText: String?, val initializerText: String)

  /** Returns the plan for [variable], or null when the fix must not be offered. Call it inside a read action. */
  fun plan(variable: UVariable): Plan? {
    if (variable !is UField && variable !is ULocalVariable) return null
    if (variable.sourcePsi?.language?.id != "kotlin") return null
    val init = variable.uastInitializer
    // A qualified call such as java.util.ArrayList(16) is wrapped in a qualified reference.
    val call = init as? UCallExpression ?: (init as? UQualifiedReferenceExpression)?.selector as? UCallExpression
      ?: return null
    val created = createdClass(call) ?: return null
    val typePsi = variable.typeReference?.sourcePsi
    val declaredType = variable.type as? PsiClassType ?: return null
    val declared = if (typePsi != null) declaredType.resolve()?.qualifiedName ?: return null else created
    val argTypes = if (typePsi != null) declaredType.parameters.toList() else call.typeArguments
    val argTexts = typeArguments(if (typePsi != null) typePsi.text else callTypeArguments(call) ?: return null)
    if (argTexts.size != argTypes.size || argTexts.isEmpty()) return null
    val names = argTypes.map { (it as? PsiClassType)?.resolve()?.qualifiedName ?: return null }
    val target = CollectionMapping.target(declared, created, names) ?: return null
    val psi = variable.sourcePsi ?: return null
    if (JavaPsiFacade.getInstance(psi.project).findClass(target.qualifiedName, psi.resolveScope) == null) return null
    val generics = if (target.args.isEmpty()) "" else target.args.joinToString(", ", "<", ">") { argTexts[it] }
    val capacity = call.valueArguments.joinToString(", ") { it.sourcePsi?.text.orEmpty() }
    val nullable = typePsi != null && typePsi.text.trimEnd().endsWith("?")
    return if (typePsi != null) {
      Plan(target, target.name + generics + (if (nullable) "?" else ""), "${target.name}($capacity)")
    } else {
      Plan(target, null, "${target.name}$generics($capacity)")
    }
  }

  /** The Java class [call] creates, or null when it is not one the fix can replace. */
  private fun createdClass(call: UCallExpression): String? {
    val args = call.valueArguments
    return when (call.kind) {
      UastCallKind.CONSTRUCTOR_CALL -> {
        if (args.size > 1 || (args.size == 1 && args[0].getExpressionType() != PsiTypes.intType())) return null
        call.resolve()?.containingClass?.qualifiedName
      }
      UastCallKind.METHOD_CALL -> {
        val created = factories[call.methodName] ?: return null
        if (args.isNotEmpty()) return null
        val owner = call.resolve()?.containingClass?.qualifiedName
        if (owner != null && !owner.startsWith("kotlin.collections.")) return null
        created
      }
      else -> null
    }
  }

  /** The `<...>` part written at a call such as `mutableListOf<String>()`, or null when there is none. */
  private fun callTypeArguments(call: UCallExpression): String? {
    val text = call.sourcePsi?.text ?: return null
    val paren = text.indexOf('(').takeIf { it >= 0 } ?: text.length
    val head = text.substring(0, paren)
    return if (head.contains('<')) head else null
  }

  /**
   * Splits the type arguments of a written type such as `MutableMap<Int, List<String>>` into `Int` and
   * `List<String>`. Arguments with variance or a star projection give an empty list, since they cannot be copied.
   */
  fun typeArguments(typeText: String): List<String> {
    val open = typeText.indexOf('<')
    val close = typeText.lastIndexOf('>')
    if (open < 0 || close < open) return emptyList()
    val parts = ArrayList<String>()
    var depth = 0
    var start = open + 1
    for (i in open + 1 until close) {
      when (typeText[i]) {
        '<', '(' -> depth++
        '>', ')' -> depth--
        ',' -> if (depth == 0) {
          parts += typeText.substring(start, i).trim()
          start = i + 1
        }
      }
    }
    parts += typeText.substring(start, close).trim()
    if (parts.any { it.isEmpty() || it == "*" || it.startsWith("out ") || it.startsWith("in ") }) return emptyList()
    return parts
  }
}
