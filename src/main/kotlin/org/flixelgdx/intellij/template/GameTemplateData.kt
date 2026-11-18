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
 * The language a game is written in.
 *
 * @property id the value written to `app.language` in `project.toml`, which is also the name of the template folder.
 */
enum class GameLanguage(val id: String) {
  JAVA("java"),
  KOTLIN("kotlin"),
}

/**
 * Everything the game templates can read, already checked and derived.
 *
 * Build it with [of] so the class name and the file name slug are derived the same way every time.
 *
 * @property title the name of the game.
 * @property company the studio or person who makes it.
 * @property version the version of the game, such as `0.1.0`.
 * @property pkg the Java package of the game code.
 * @property className the name of the main game class, such as `MyGame`.
 * @property language the language of the game code.
 * @property platforms the platforms the game builds for besides `core`, in the order of [GameTemplates.PLATFORMS].
 * @property frameworkVersion the version of the framework, such as `0.7.2`.
 * @property ktxVersion the version of the Kotlin extension of FlixelGDX; only used by Kotlin games.
 * @property folder the name of the project folder, used as the Gradle project name.
 * @property slug a lowercase file name made from the title, used for the web files and the desktop jar.
 * @property width the window width in pixels.
 * @property height the window height in pixels.
 */
data class GameTemplateData(
  val title: String,
  val company: String,
  val version: String,
  val pkg: String,
  val className: String,
  val language: GameLanguage,
  val platforms: List<String>,
  val frameworkVersion: String,
  val ktxVersion: String,
  val folder: String,
  val slug: String,
  val width: Int,
  val height: Int,
) {

  /** Whether the game is written in Kotlin. */
  val kotlin: Boolean get() = language == GameLanguage.KOTLIN

  /** Whether the game builds for [platform]. */
  fun hasPlatform(platform: String): Boolean = platform in platforms

  /** The values the templates read, under the names the templates use (see [TextTemplate]). */
  fun values(): Map<String, Any> = buildMap {
    put("title", title)
    put("company", company)
    put("version", version)
    put("package", pkg)
    put("className", className)
    put("language", language.id)
    put("kotlin", kotlin)
    put("frameworkVersion", frameworkVersion)
    put("ktxVersion", ktxVersion)
    put("folder", folder)
    put("slug", slug)
    put("width", width)
    put("height", height)
    put("platformList", platforms.joinToString(", ") { "\"$it\"" })
    put("buildSrcBundle", buildSrcBundle().joinToString(", ") { "\"$it\"" })
    for (platform in GameTemplates.PLATFORMS) put(platform, hasPlatform(platform))
  }

  /**
   * The Gradle plugins that `buildSrc` puts on the build classpath, in catalog order: Kotlin's plugin for Kotlin
   * games, R8 and the Android Gradle plugin for Android, and TeaVM with the FlixelGDX web plugin for the web.
   */
  private fun buildSrcBundle(): List<String> = buildList {
    if (kotlin) add("kotlin-gradle-plugin")
    if (hasPlatform(ANDROID)) add("r8")
    if (hasPlatform(HTML5)) {
      add("teavm-gradle-plugin")
      add("flixelgdx-html5-plugin")
    }
    if (hasPlatform(ANDROID)) add("gradle")
  }

  companion object {
    private const val HTML5 = "html5"
    private const val ANDROID = "android"

    /**
     * Builds the data for a new game, deriving the class name from the title and the slug from the title.
     *
     * The platforms are put in the usual order and duplicates are dropped, so the files never depend on the order in
     * which the person ticked the boxes.
     */
    fun of(
      title: String,
      company: String,
      version: String,
      pkg: String,
      language: GameLanguage,
      platforms: Collection<String>,
      frameworkVersion: String,
      ktxVersion: String,
      folder: String,
      width: Int,
      height: Int,
    ): GameTemplateData = GameTemplateData(
      title = title,
      company = company,
      version = version,
      pkg = pkg,
      className = GameNames.className(title),
      language = language,
      platforms = GameTemplates.PLATFORMS.filter { it in platforms },
      frameworkVersion = frameworkVersion,
      ktxVersion = ktxVersion,
      folder = folder,
      slug = GameNames.slug(title),
      width = width,
      height = height,
    )
  }
}
