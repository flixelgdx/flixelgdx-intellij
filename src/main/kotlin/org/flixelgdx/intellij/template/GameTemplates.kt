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
package org.flixelgdx.intellij.template

/**
 * One file of a game, ready to be written.
 *
 * @property path the slash-separated path relative to the project folder.
 * @property content the bytes of the file.
 * @property executable whether the file must be executable, which is true for the `gradlew` script.
 */
class GameFile(val path: String, val content: ByteArray, val executable: Boolean) {

  /** The content as UTF-8 text. Only meaningful for text files. */
  fun text(): String = content.toString(Charsets.UTF_8)
}

/**
 * Holds the files of a new FlixelGDX game and renders them.
 *
 * The files live in the plugin's resources under `org/flixelgdx/intellij/templates`, in layers from the most general
 * to the most specific:
 *
 * ```
 * common/                 build files and settings every game gets
 * <language>/base/        what a language adds to every template, such as the Gradle convention plugin
 * <language>/<template>/  the source code of one template
 * ```
 *
 * A later layer replaces a file of an earlier one that ends up at the same path. Think of it as three sheets of
 * tracing paper laid on top of each other: what is drawn on a higher sheet hides what is under it.
 *
 * File names follow a few rules, so the folders can hold files that the build or Git would otherwise treat as their
 * own:
 *
 * - A name ending in `.tmpl` is rendered with [TextTemplate] and loses the suffix. Any other file is copied as it is,
 *   byte for byte, which keeps `gradle-wrapper.jar` intact.
 * - `dot_` at the start of a name becomes a dot, so `dot_gitignore` becomes `.gitignore`. A real `.gitignore` here
 *   would apply to this repository, and Gradle leaves such files out of the plugin's resources by default.
 * - A folder called `__package__` becomes the Java package as nested folders, and `__Class__` in a name becomes the
 *   class name of the game.
 * - A top-level `desktop`, `html5` or `android` folder is a module. Its files are only created when the game builds
 *   for that platform. `core` is always created.
 * - `gradlew` is marked executable, and `gradlew.bat` always gets Windows line endings.
 *
 * Resources inside a jar cannot be listed, so `index.txt` names every file; a unit test keeps it in step with the
 * folder.
 *
 * Example:
 * ```
 * val files = GameTemplates.render(data, GameTemplates.DEFAULT)
 * ProjectWriter.write(dir, files)
 * ```
 */
object GameTemplates {

  /** The template used when none is chosen. */
  const val DEFAULT = "blank"

  /** The platforms a game can build for besides `core`, in the order they are always listed. */
  val PLATFORMS: List<String> = listOf("desktop", "html5", "android")

  /** The folder of the template files inside the plugin's resources. */
  const val ROOT = "org/flixelgdx/intellij/templates"

  private const val INDEX = "index.txt"
  private const val COMMON = "common"
  private const val BASE = "base"
  private const val TMPL = ".tmpl"
  private const val DOT = "dot_"
  private const val PACKAGE = "__package__"
  private const val CLASS = "__Class__"

  /** Every file of every layer, relative to [ROOT], read once from the index. */
  val index: List<String> by lazy {
    String(resource(INDEX), Charsets.UTF_8).lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
  }

  /** The names of the templates that exist for [language], sorted. */
  fun names(language: GameLanguage): List<String> =
    index.asSequence()
      .filter { it.startsWith("${language.id}/") }
      .map { it.split('/')[1] }
      .filter { it != BASE }
      .distinct()
      .sorted()
      .toList()

  /**
   * Builds every file of the template [name] for a game, in memory. The files come back sorted by path.
   *
   * @throws IllegalArgumentException when there is no such template for the language of [data].
   * @throws TemplateException when a template file is broken, which is a bug in the plugin.
   */
  fun render(data: GameTemplateData, name: String): List<GameFile> {
    require(name in names(data.language)) {
      "There is no \"$name\" template for ${data.language.id}. The templates are: " +
        names(data.language).joinToString(", ")
    }
    val values = data.values()
    val byPath = LinkedHashMap<String, GameFile>()
    for (layer in listOf(COMMON, "${data.language.id}/$BASE", "${data.language.id}/$name")) {
      for (entry in index) {
        if (!entry.startsWith("$layer/")) continue
        val file = build(entry, entry.removePrefix("$layer/"), data, values) ?: continue
        byPath[file.path] = file
      }
    }
    return byPath.values.sortedBy { it.path }
  }

  /** Turns one resource into a [GameFile], or `null` for a file of a module the game does not have. */
  private fun build(resourcePath: String, rel: String, data: GameTemplateData, values: Map<String, Any>): GameFile? {
    val segments = rel.split('/').toMutableList()
    val module = segments[0]
    if (segments.size > 1 && module in PLATFORMS && !data.hasPlatform(module)) return null
    for (i in segments.indices) {
      val seg = segments[i]
      segments[i] = when {
        seg == PACKAGE -> data.pkg.replace('.', '/')
        seg.startsWith(CLASS) -> data.className + seg.removePrefix(CLASS)
        seg.startsWith(DOT) -> "." + seg.removePrefix(DOT)
        else -> seg
      }
    }
    val last = segments.lastIndex
    val isTemplate = segments[last].endsWith(TMPL)
    segments[last] = segments[last].removeSuffix(TMPL)
    var content = resource(resourcePath)
    if (isTemplate) {
      content = TextTemplate.render(rel, String(content, Charsets.UTF_8), values).toByteArray(Charsets.UTF_8)
    }
    val fileName = segments[last]
    if (fileName == "gradlew.bat") content = toCrlf(content)
    return GameFile(segments.joinToString("/"), content, executable = fileName == "gradlew")
  }

  private fun toCrlf(bytes: ByteArray): ByteArray =
    String(bytes, Charsets.UTF_8).replace("\r\n", "\n").replace("\n", "\r\n").toByteArray(Charsets.UTF_8)

  private fun resource(path: String): ByteArray {
    val stream = GameTemplates::class.java.classLoader.getResourceAsStream("$ROOT/$path")
      ?: throw IllegalStateException("The plugin is missing the template file $path")
    return stream.use { it.readBytes() }
  }
}
