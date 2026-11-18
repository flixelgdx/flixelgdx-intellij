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

import org.flixelgdx.intellij.template.GameLanguage
import org.toml.lang.psi.TomlArray
import org.toml.lang.psi.TomlFile

/**
 * The settings of a game as written in its `project.toml`.
 *
 * @property title the name of the game.
 * @property company the studio or person.
 * @property version the version of the game.
 * @property pkg the Java package.
 * @property language the language of the game code.
 * @property platforms the enabled platforms, in file order.
 * @property width the window width in pixels.
 * @property height the window height in pixels.
 */
data class GameSettings(
  val title: String,
  val company: String,
  val version: String,
  val pkg: String,
  val language: GameLanguage,
  val platforms: List<String>,
  val width: Int,
  val height: Int,
)

/**
 * Reads `project.toml` and plans changes to its `platforms` list.
 *
 * `platforms` is a top-level key such as `platforms = ["desktop", "html5"]`. The FlixelGDX settings plugin reads it at
 * every Gradle sync and includes one module per platform, so adding a platform here is what turns its module on.
 * Read it inside a read action.
 *
 * Example:
 * ```
 * val edits = GameProjectToml(file).addPlatform("android")
 * ```
 */
class GameProjectToml(private val file: TomlFile) {

  /** The enabled platforms in file order, without duplicates, or an empty list when the key is missing. */
  fun platforms(): List<String> = array()?.let(TomlEdits::strings)?.distinct().orEmpty()

  /** Reads the settings of the game. Missing values become empty text and the usual defaults. */
  fun settings(): GameSettings {
    fun app(key: String) = TomlEdits.string(TomlEdits.entry(file, APP, key)?.value).orEmpty()
    fun window(key: String, default: Int) =
      TomlEdits.entry(file, WINDOW, key)?.value?.text?.trim()?.toIntOrNull() ?: default
    val language = GameLanguage.entries.firstOrNull { it.id == app("language") } ?: GameLanguage.JAVA
    return GameSettings(
      title = app("title"),
      company = app("company"),
      version = app("version"),
      pkg = app("package"),
      language = language,
      platforms = platforms(),
      width = window("width", DEFAULT_WIDTH),
      height = window("height", DEFAULT_HEIGHT),
    )
  }

  /**
   * Plans adding [platform] to the `platforms` list. A missing key is added at the top of the file, where it has to
   * be (above the first table).
   *
   * @return the edits, or an empty list when the platform is already there.
   */
  fun addPlatform(platform: String): List<TextEdit> {
    val array = array()
    if (array == null) return listOf(TextEdit(0, 0, "$KEY = [${TomlEdits.quote(platform)}]\n"))
    if (platform in TomlEdits.strings(array)) return emptyList()
    return listOf(TomlEdits.appendToArray(array, platform))
  }

  /** Plans taking [platform] out of the `platforms` list, or returns an empty list when it is not there. */
  fun removePlatform(platform: String): List<TextEdit> =
    array()?.let { TomlEdits.removeFromArray(it, platform) }?.let(::listOf).orEmpty()

  private fun array(): TomlArray? = TomlEdits.entry(file, null, KEY)?.value as? TomlArray

  companion object {
    /** Where the file lives, relative to the project folder. */
    const val PATH = "project.toml"

    private const val KEY = "platforms"
    private const val APP = "app"
    private const val WINDOW = "window"
    private const val DEFAULT_WIDTH = 640
    private const val DEFAULT_HEIGHT = 360
  }
}

/**
 * Brings the entries that a freshly rendered catalog has, but the game's catalog lacks, into the game's catalog.
 *
 * When a platform is added, the framework needs new catalog entries (such as `flixelgdx-android` and the Android
 * Gradle plugin). Instead of keeping a second list of those entries in code, the plugin renders the catalog template
 * for the new set of platforms and copies over only what is missing: new `[versions]` and `[libraries]` lines, and
 * new names in each bundle. Nothing that is already there is changed, so versions the person picked stay as they are.
 */
object CatalogMerge {

  private val TABLES = listOf("versions", "libraries")
  private const val BUNDLES = "bundles"

  /**
   * Plans the edits that add to [current] what [wanted] has and [current] lacks.
   *
   * @param current the game's catalog.
   * @param wanted a catalog rendered from the template, usually not attached to any project.
   */
  fun missing(current: TomlFile, wanted: TomlFile): List<TextEdit> {
    val edits = ArrayList<TextEdit>()
    for (table in TABLES) {
      val have = TomlEdits.entries(current, table).map { TomlEdits.keyName(it.key) }.toSet()
      val lines = TomlEdits.entries(wanted, table).filter { TomlEdits.keyName(it.key) !in have }.map { it.text }
      if (lines.isNotEmpty()) edits += TomlEdits.addEntry(current, table, lines.joinToString("\n"))
    }
    val newBundles = ArrayList<String>()
    for (entry in TomlEdits.entries(wanted, BUNDLES)) {
      val name = TomlEdits.keyName(entry.key)
      val items = (entry.value as? TomlArray)?.let(TomlEdits::strings).orEmpty()
      val existing = TomlEdits.entry(current, BUNDLES, name)
      val array = existing?.value as? TomlArray
      when {
        existing == null -> newBundles += entry.text
        array != null -> {
          val have = TomlEdits.strings(array)
          val add = items.filter { it !in have }
          if (add.isNotEmpty()) edits += TomlEdits.appendToArray(array, add)
        }
      }
    }
    if (newBundles.isNotEmpty()) edits += TomlEdits.addEntry(current, BUNDLES, newBundles.joinToString("\n"))
    return edits
  }
}
