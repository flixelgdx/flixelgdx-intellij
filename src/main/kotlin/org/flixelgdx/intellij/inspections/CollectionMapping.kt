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
 * The FlixelGDX collection that replaces a standard Java collection.
 *
 * @property name the simple name of the FlixelGDX class (it lives in [CollectionMapping.PACKAGE]).
 * @property args the positions of the original type arguments to keep, in order. For example `FlixelIntMap<V>`
 * keeps only the second argument of `Map<Integer, V>`, so its `args` are `[1]`.
 */
data class Target(val name: String, val args: List<Int>) {

  /** The fully qualified class name. */
  val qualifiedName: String get() = "${CollectionMapping.PACKAGE}.$name"
}

/**
 * Maps standard Java collections to the garbage-free collections of the framework.
 *
 * Java's collections create an iterator object on every for-each loop, box every `int` and `float` that is stored
 * in them, and carry extra bookkeeping. The framework's `org.flixelgdx.collections` package has replacements that
 * allocate nothing while a game runs. This object holds the rules in one place, so they can be tested without an IDE.
 *
 * The mapping the plugin uses:
 *
 * - `List`/`ArrayList` become `FlixelArray<T>`, the general-purpose growable list (`FlixelList` is only the read-only
 *   view of it), or a primitive array (`FlixelIntArray`, `FlixelFloatArray`, `FlixelLongArray`, `FlixelShortArray`,
 *   `FlixelByteArray`, `FlixelCharArray`, `FlixelBooleanArray`) when the element is a boxed primitive that has one.
 * - `Set`/`HashSet` become `FlixelSet<T>`, `FlixelIntSet` for `Integer` and `FlixelLongSet` for `Long`.
 * - `Map`/`HashMap` become `FlixelMap<K, V>`, `FlixelIntMap<V>` for `Integer` keys, `FlixelLongMap<V>` for `Long`
 *   keys, `FlixelObjectIntMap<K>` for `Integer` values and `FlixelObjectFloatMap<K>` for `Float` values.
 * - `IdentityHashMap` becomes `FlixelIdentityMap<K, V>`.
 *
 * Ordered or sorted collections (`LinkedHashMap`, `TreeMap`, `LinkedList`, `PriorityQueue` and so on) have no
 * equivalent with the same behavior, so they are only reported and [suggestion] names the closest class.
 */
object CollectionMapping {

  /** The package of the framework's collections. */
  const val PACKAGE = "org.flixelgdx.collections"

  private const val INT = "java.lang.Integer"
  private const val LONG = "java.lang.Long"
  private const val FLOAT = "java.lang.Float"

  private val primitiveArrays = mapOf(
    INT to "FlixelIntArray",
    LONG to "FlixelLongArray",
    FLOAT to "FlixelFloatArray",
    "java.lang.Short" to "FlixelShortArray",
    "java.lang.Byte" to "FlixelByteArray",
    "java.lang.Character" to "FlixelCharArray",
    "java.lang.Boolean" to "FlixelBooleanArray",
  )

  private val lists = setOf("java.util.List", "java.util.ArrayList")
  private val sets = setOf("java.util.Set", "java.util.HashSet")
  private val maps = setOf("java.util.Map", "java.util.HashMap")
  private val identityMaps = setOf("java.util.Map", "java.util.IdentityHashMap")

  /** Every `java.util` collection type the inspection reports. */
  val flagged: Set<String> = setOf(
    "java.util.Collection",
    "java.util.List",
    "java.util.ArrayList",
    "java.util.LinkedList",
    "java.util.Vector",
    "java.util.Stack",
    "java.util.Queue",
    "java.util.Deque",
    "java.util.ArrayDeque",
    "java.util.PriorityQueue",
    "java.util.Set",
    "java.util.HashSet",
    "java.util.LinkedHashSet",
    "java.util.TreeSet",
    "java.util.SortedSet",
    "java.util.NavigableSet",
    "java.util.Map",
    "java.util.HashMap",
    "java.util.LinkedHashMap",
    "java.util.TreeMap",
    "java.util.SortedMap",
    "java.util.NavigableMap",
    "java.util.IdentityHashMap",
    "java.util.WeakHashMap",
    "java.util.Hashtable",
  )

  /** Kotlin helper functions that create standard collections, with the framework class to use instead. */
  val kotlinFactories: Map<String, String> = mapOf(
    "listOf" to "FlixelArray",
    "mutableListOf" to "FlixelArray",
    "arrayListOf" to "FlixelArray",
    "setOf" to "FlixelSet",
    "mutableSetOf" to "FlixelSet",
    "hashSetOf" to "FlixelSet",
    "linkedSetOf" to "FlixelSet",
    "sortedSetOf" to "FlixelSet",
    "mapOf" to "FlixelMap",
    "mutableMapOf" to "FlixelMap",
    "hashMapOf" to "FlixelMap",
    "linkedMapOf" to "FlixelMap",
    "sortedMapOf" to "FlixelMap",
  )

  /** Returns the name of the framework class that comes closest to [javaType], for the message. */
  fun suggestion(javaType: String): String = when (javaType.substringAfterLast('.')) {
    "IdentityHashMap" -> "FlixelIdentityMap"
    "Map", "HashMap", "LinkedHashMap", "TreeMap", "SortedMap", "NavigableMap", "WeakHashMap", "Hashtable" -> "FlixelMap"
    "Set", "HashSet", "LinkedHashSet", "TreeSet", "SortedSet", "NavigableSet" -> "FlixelSet"
    else -> "FlixelArray"
  }

  /**
   * Finds the replacement for a declaration `declared<typeArgs> x = new created<>(...)`, or null when there is no
   * replacement with the same behavior.
   *
   * @param declared the qualified name of the declared type (for example `java.util.Map`).
   * @param created the qualified name of the class that is created (for example `java.util.HashMap`).
   * @param typeArgs the qualified names of the declared type arguments. A null entry is an argument that is not a
   * plain class (a wildcard, an array or a type variable).
   */
  fun target(declared: String, created: String, typeArgs: List<String?>): Target? {
    if (declared !in lists && declared !in sets && declared !in maps && declared != created) return null
    if (declared == "java.util.IdentityHashMap" || created == "java.util.IdentityHashMap") {
      return if (declared in identityMaps && created == "java.util.IdentityHashMap" && typeArgs.size == 2) {
        Target("FlixelIdentityMap", listOf(0, 1))
      } else {
        null
      }
    }
    return when {
      declared in lists && created == "java.util.ArrayList" -> list(typeArgs)
      declared in sets && created == "java.util.HashSet" -> set(typeArgs)
      declared in maps && created == "java.util.HashMap" -> map(typeArgs)
      else -> null
    }
  }

  private fun list(args: List<String?>): Target? {
    if (args.size != 1) return null
    val element = args[0]
    val primitive = primitiveArrays[element]
    return if (primitive != null) Target(primitive, emptyList()) else Target("FlixelArray", listOf(0))
  }

  private fun set(args: List<String?>): Target? {
    if (args.size != 1) return null
    return when (args[0]) {
      INT -> Target("FlixelIntSet", emptyList())
      LONG -> Target("FlixelLongSet", emptyList())
      else -> Target("FlixelSet", listOf(0))
    }
  }

  private fun map(args: List<String?>): Target? {
    if (args.size != 2) return null
    return when {
      args[0] == INT -> Target("FlixelIntMap", listOf(1))
      args[0] == LONG -> Target("FlixelLongMap", listOf(1))
      args[1] == INT -> Target("FlixelObjectIntMap", listOf(0))
      args[1] == FLOAT -> Target("FlixelObjectFloatMap", listOf(0))
      else -> Target("FlixelMap", listOf(0, 1))
    }
  }
}
