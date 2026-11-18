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
package org.flixelgdx.intellij.toml

import org.toml.lang.psi.TomlArray
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlInlineTable
import org.toml.lang.psi.TomlKeyValue

/**
 * One entry of the `[libraries]` table.
 *
 * @property alias the name of the entry, such as `flixelgdx-core`.
 * @property module `group:artifact`, or an empty string when the entry does not say.
 * @property version the version written in the entry, or an empty string when it uses a reference.
 * @property versionRef the key in `[versions]` that the entry points at, or an empty string.
 */
data class CatalogLibrary(val alias: String, val module: String, val version: String, val versionRef: String) {

  /** Whether the entry belongs to the framework itself, which the plugin never removes. */
  val framework: Boolean get() = alias in VersionCatalog.FRAMEWORK_ALIASES

  /** A readable name for lists, such as `flixelgdx-ui (org.flixelgdx:flixelgdx-ui)`. */
  val label: String get() = if (module.isEmpty()) alias else "$alias ($module)"
}

/**
 * Reads and plans changes to the version catalog of a game, `gradle/libs.versions.toml`.
 *
 * The catalog is where every library of the game is listed. Modules do not name libraries directly; they use a
 * bundle, such as `libs.bundles.core` in `core/build.gradle.kts`. So adding a library means two small edits: an entry
 * in `[libraries]`, and its alias appended to the bundle of the module that needs it. Think of `[libraries]` as the
 * pantry and each bundle as the shopping list of one kitchen.
 *
 * Every change is planned as [TextEdit]s with [TomlEdits], so the comments and layout of the file are kept. Read the
 * catalog inside a read action.
 *
 * Example:
 * ```
 * val catalog = VersionCatalog(tomlFile)
 * val edits = catalog.addLibrary("flixelgdx-ui", Coordinates.parse("org.flixelgdx:flixelgdx-ui:0.7.2")!!, "core")
 * TomlEdits.apply(document, edits)
 * ```
 */
class VersionCatalog(private val file: TomlFile) {

  /** Every entry of `[libraries]`, in file order. */
  fun libraries(): List<CatalogLibrary> = TomlEdits.entries(file, LIBRARIES).map(::library)

  /** The names of the bundles in `[bundles]`, in file order. */
  fun bundles(): List<String> = TomlEdits.entries(file, BUNDLES).map { TomlEdits.keyName(it.key) }

  /** The aliases listed in the bundle [name], or an empty list when there is no such bundle. */
  fun bundle(name: String): List<String> =
    (TomlEdits.entry(file, BUNDLES, name)?.value as? TomlArray)?.let(TomlEdits::strings).orEmpty()

  /** The version stored under [key] in `[versions]`, or `null` when there is none. */
  fun version(key: String): String? = TomlEdits.string(TomlEdits.entry(file, VERSIONS, key)?.value)

  /** Whether `[libraries]` has an entry called [alias]. */
  fun hasLibrary(alias: String): Boolean = TomlEdits.entry(file, LIBRARIES, alias) != null

  /**
   * Plans adding a library: a `[libraries]` entry with the version written in it, and [alias] appended to [bundle].
   * A missing bundle is created.
   *
   * @throws IllegalArgumentException when [alias] is already used.
   */
  fun addLibrary(alias: String, coordinates: Coordinates, bundle: String): List<TextEdit> {
    require(!hasLibrary(alias)) { "The catalog already has a library called $alias." }
    val line = "$alias = { module = ${TomlEdits.quote(coordinates.module)}, " +
      "version = ${TomlEdits.quote(coordinates.version)} }"
    val edits = arrayListOf(TomlEdits.addEntry(file, LIBRARIES, line))
    val entry = TomlEdits.entry(file, BUNDLES, bundle)
    val array = entry?.value as? TomlArray
    edits += when {
      array != null -> TomlEdits.appendToArray(array, alias)
      else -> TomlEdits.addEntry(file, BUNDLES, "$bundle = [${TomlEdits.quote(alias)}]")
    }
    return edits
  }

  /**
   * Plans removing the library [alias]: its `[libraries]` entry, its name in every bundle, and the `[versions]` entry
   * it points at when no other library uses that version.
   *
   * @return the edits, or an empty list when there is no such library.
   */
  fun removeLibrary(alias: String): List<TextEdit> {
    val entry = TomlEdits.entry(file, LIBRARIES, alias) ?: return emptyList()
    val edits = arrayListOf(TomlEdits.removeEntry(entry))
    for (bundle in TomlEdits.entries(file, BUNDLES)) {
      val array = bundle.value as? TomlArray ?: continue
      TomlEdits.removeFromArray(array, alias)?.let(edits::add)
    }
    val ref = library(entry).versionRef
    val shared = libraries().any { it.alias != alias && it.versionRef == ref }
    if (ref.isNotEmpty() && ref != FRAMEWORK_VERSION && !shared) {
      TomlEdits.entry(file, VERSIONS, ref)?.let { edits += TomlEdits.removeEntry(it) }
    }
    return edits
  }

  /** Plans setting `[versions] key` to [value], or returns an empty list when the catalog has no such version. */
  fun setVersion(key: String, value: String): List<TextEdit> =
    TomlEdits.entry(file, VERSIONS, key)?.let { listOf(TomlEdits.setString(it, value)) }.orEmpty()

  private fun library(entry: TomlKeyValue): CatalogLibrary {
    val alias = TomlEdits.keyName(entry.key)
    val value = entry.value
    TomlEdits.string(value)?.let { text ->
      val parts = text.split(':')
      return CatalogLibrary(alias, parts.take(2).joinToString(":"), parts.getOrElse(2) { "" }, "")
    }
    val table = value as? TomlInlineTable ?: return CatalogLibrary(alias, "", "", "")
    val fields = table.entries.associate { TomlEdits.keyName(it.key) to it.value }
    val module = TomlEdits.string(fields["module"])
      ?: listOfNotNull(TomlEdits.string(fields["group"]), TomlEdits.string(fields["name"]))
        .takeIf { it.size == 2 }?.joinToString(":")
      ?: ""
    val versionValue = fields["version"]
    val ref = TomlEdits.string(fields["version.ref"])
      ?: (versionValue as? TomlInlineTable)?.entries
        ?.firstOrNull { TomlEdits.keyName(it.key) == "ref" }?.let { TomlEdits.string(it.value) }
      ?: ""
    return CatalogLibrary(alias, module, TomlEdits.string(versionValue).orEmpty(), ref)
  }

  companion object {
    /** Where the catalog lives, relative to the project folder. */
    const val PATH = "gradle/libs.versions.toml"

    /** The `[versions]` key of the framework. */
    const val FRAMEWORK_VERSION = "flixelgdx"

    /** The `[versions]` key of the Kotlin extension of the framework. */
    const val KTX_VERSION = "flixelgdx-ktx"

    /** The bundles the build files of a game use, in the order the templates write them. */
    val STANDARD_BUNDLES = listOf(
      "core", "desktop", "html5", "android",
      "coreProcessors", "desktopProcessors", "html5Processors", "androidProcessors",
      "androidDesugar", "buildsrc",
    )

    /** The `[libraries]` entries of the framework itself. */
    val FRAMEWORK_ALIASES = setOf(
      "flixelgdx-core", "flixelgdx-desktop", "flixelgdx-html5", "flixelgdx-android", "flixelgdx-html5-plugin",
    )

    private const val VERSIONS = "versions"
    private const val LIBRARIES = "libraries"
    private const val BUNDLES = "bundles"
  }
}
