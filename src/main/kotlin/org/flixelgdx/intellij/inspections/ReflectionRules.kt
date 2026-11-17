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

/**
 * Decides which method calls count as reflection.
 *
 * Reflection looks code up by name at run time. That breaks on platforms that compile the game ahead of time (the web
 * build removes or renames what it thinks is unused, and Android's R8 shrinker does the same), so the framework
 * avoids it completely.
 */
object ReflectionRules {

  private const val CLASS = "java.lang.Class"
  private const val LOOKUP = "java.lang.invoke.MethodHandles.Lookup"

  private val classMembers = setOf(
    "forName",
    "newInstance",
    "getMethod",
    "getMethods",
    "getDeclaredMethod",
    "getDeclaredMethods",
    "getField",
    "getFields",
    "getDeclaredField",
    "getDeclaredFields",
    "getConstructor",
    "getConstructors",
    "getDeclaredConstructor",
    "getDeclaredConstructors",
    "getClasses",
    "getDeclaredClasses",
    "getRecordComponents",
  )

  /** Packages whose every member is reflection (`Method`, `Field`, `Proxy` and so on). */
  private val reflectPackages = listOf("java.lang.reflect.", "kotlin.reflect.full.", "kotlin.reflect.jvm.")

  /**
   * Returns true when calling [name] on a method declared in [owner] is reflection.
   *
   * @param owner the qualified name of the class that declares the method.
   * @param name the method name.
   */
  fun isReflection(owner: String, name: String): Boolean = when {
    owner == "java.lang.reflect.Modifier" -> false
    reflectPackages.any { owner.startsWith(it) } -> true
    owner == CLASS -> name in classMembers
    owner == LOOKUP -> name.startsWith("find") || name.startsWith("unreflect")
    else -> false
  }

  /** Returns true when a Kotlin import of [path] brings in reflection helpers (for example `kotlin.reflect.full.*`). */
  fun isReflectionImport(path: String): Boolean =
    reflectPackages.any { path.startsWith(it) || "$path." == it }
}
