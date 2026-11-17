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
 * One group of library APIs that FlixelGDX games must not use.
 *
 * @property owners the classes the rule is about. An entry ending in `.*` means every class of that package.
 * @property members method names to flag on those owners. Empty means the owner itself is the problem (any use of
 * the type is flagged) and methods are not checked.
 * @property reason a short sentence that says why the API is a problem.
 * @property replacements the suggested replacement, by member name. The empty name is the default for the rule.
 */
class ApiRule(
  val owners: Set<String>,
  val members: Set<String>,
  val reason: String,
  val replacements: Map<String, String> = emptyMap(),
) {

  /** Returns the suggested replacement for [member], or null when there is none. */
  fun replacementFor(member: String?): String? = replacements[member] ?: replacements[""]

  /** Returns true when [qualifiedName] is one of the owners of this rule. */
  fun ownsClass(qualifiedName: String): Boolean = owners.any {
    if (it.endsWith(".*")) qualifiedName.startsWith(it.removeSuffix("*")) else qualifiedName == it
  }
}

/**
 * The list of APIs that are missing on Android (ART only ships the Java 17 library) or on the web (TeaVM), taken from
 * the framework's rules.
 *
 * This is the one place to edit when the list changes: add an [ApiRule] to [classes] (the type itself is the problem)
 * or to [methods] (only some methods of an otherwise fine type are the problem).
 */
object UnsupportedApis {

  private const val SEQUENCED_REASON = "it was added in Java 21 and Android's runtime does not have it."
  private val sequencedNames =
    setOf("getFirst", "getLast", "addFirst", "addLast", "removeFirst", "removeLast", "reversed")
  private val sequencedReplacements = mapOf(
    "getFirst" to "get(0)",
    "getLast" to "get(size() - 1)",
    "addFirst" to "add(0, value)",
    "addLast" to "add(value)",
    "removeFirst" to "remove(0)",
    "removeLast" to "remove(size() - 1)",
    "reversed" to "an indexed loop that walks backwards",
  )

  /** Rules about whole types. */
  val classes: List<ApiRule> = listOf(
    ApiRule(
      setOf("java.lang.foreign.*"),
      emptySet(),
      "the foreign function and memory API needs a very new Java and does not exist on Android or the web.",
      mapOf("" to "java.nio buffers or the framework's own APIs"),
    ),
    ApiRule(
      setOf("java.lang.ScopedValue"),
      emptySet(),
      "scoped values are a preview feature that Android and the web do not have.",
      mapOf("" to "a plain field or a method parameter"),
    ),
    ApiRule(
      setOf("java.util.concurrent.StructuredTaskScope"),
      emptySet(),
      "structured concurrency is a preview feature that Android and the web do not have.",
      mapOf("" to "a regular ExecutorService"),
    ),
    ApiRule(
      setOf("java.lang.classfile.*"),
      emptySet(),
      "the class file API does not exist on Android or the web.",
      mapOf("" to "bytecode work in a build-time tool instead of game code"),
    ),
    ApiRule(
      setOf("java.util.HexFormat"),
      emptySet(),
      "it was added after Java 17 support on Android and the web is missing it.",
      mapOf("" to "Integer.toHexString(...) and Integer.parseInt(text, 16)"),
    ),
  )

  /** Rules about single methods. */
  val methods: List<ApiRule> = listOf(
    ApiRule(
      setOf("java.lang.Thread"),
      setOf("ofVirtual", "startVirtualThread", "isVirtual"),
      "virtual threads need Java 21 and are not available on Android or the web.",
      mapOf("" to "a regular thread or a fixed thread pool"),
    ),
    ApiRule(
      setOf("java.util.concurrent.Executors"),
      setOf("newVirtualThreadPerTaskExecutor"),
      "virtual threads need Java 21 and are not available on Android or the web.",
      mapOf("" to "Executors.newFixedThreadPool(...)"),
    ),
    ApiRule(
      setOf("java.lang.Thread"),
      setOf("threadId"),
      "it was added in Java 19 and Android's runtime does not have it.",
      mapOf("" to "Thread.getId()"),
    ),
    ApiRule(
      setOf("java.lang.Math", "java.lang.StrictMath"),
      setOf("clamp"),
      "it was added in Java 21 and Android's runtime does not have it.",
      mapOf("" to "FlixelMath.clamp(...)"),
    ),
    ApiRule(
      setOf("java.lang.StringBuilder", "java.lang.StringBuffer", "java.lang.AbstractStringBuilder"),
      setOf("repeat"),
      "it was added in Java 21 and Android's runtime does not have it.",
      mapOf("" to "a for loop that calls append(...)"),
    ),
    ApiRule(
      setOf("java.util.Locale"),
      setOf("of"),
      "it was added in Java 19 and Android's runtime does not have it.",
      mapOf("" to "Locale.forLanguageTag(...)"),
    ),
    ApiRule(
      setOf(
        "java.util.SequencedCollection",
        "java.util.SequencedSet",
        "java.util.List",
        "java.util.ArrayList",
        "java.util.LinkedHashSet",
        "java.util.SortedSet",
        "java.util.NavigableSet",
        "java.util.TreeSet",
        "java.util.concurrent.CopyOnWriteArrayList",
      ),
      sequencedNames,
      SEQUENCED_REASON,
      sequencedReplacements,
    ),
    ApiRule(
      setOf("java.util.Deque", "java.util.LinkedList", "java.util.ArrayDeque"),
      setOf("reversed"),
      SEQUENCED_REASON,
      mapOf("" to "an indexed loop that walks backwards"),
    ),
    ApiRule(
      setOf("java.util.SequencedMap", "java.util.LinkedHashMap", "java.util.SortedMap", "java.util.NavigableMap"),
      setOf("putFirst", "putLast", "reversed", "sequencedKeySet", "sequencedValues", "sequencedEntrySet"),
      SEQUENCED_REASON,
    ),
  )

  /** Finds the rule that forbids using the type [qualifiedName], or null. */
  fun forClass(qualifiedName: String): ApiRule? = classes.firstOrNull { it.ownsClass(qualifiedName) }

  /** Finds the rule that forbids calling [name] on a method declared in [owner], or null. */
  fun forMethod(owner: String, name: String): ApiRule? =
    methods.firstOrNull { name in it.members && it.ownsClass(owner) }

  /** Builds the message shown for a problem found by [rule]. [what] is the name the user wrote. */
  fun message(rule: ApiRule, what: String, member: String? = null): String {
    val advice = rule.replacementFor(member)?.let { " Use $it instead." } ?: ""
    return "'$what' is not safe in FlixelGDX games: ${rule.reason}$advice"
  }
}
