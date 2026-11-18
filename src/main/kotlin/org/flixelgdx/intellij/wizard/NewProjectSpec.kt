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
package org.flixelgdx.intellij.wizard

import org.flixelgdx.intellij.template.GameLanguage
import org.flixelgdx.intellij.template.GameTemplateData
import org.flixelgdx.intellij.template.GameTemplates
import java.nio.file.Path

/**
 * Everything the New Project wizard collects about a new game.
 *
 * It is plain data on purpose: the wizard fills it in, [templateData] turns it into what the templates read, and tests
 * can check that without any UI. The title, company and version are expected to be trimmed already (the wizard trims
 * them).
 *
 * Example:
 * ```
 * val spec = NewProjectSpec(dir, "My Game", "My Studio", "0.1.0", "com.mystudio.mygame")
 * val files = GameTemplates.render(spec.templateData("0.7.2", "0.7.2"), spec.template)
 * ```
 *
 * @property dir the folder of the new project.
 * @property title the name of the game.
 * @property company the studio or person.
 * @property version the game version.
 * @property pkg the Java package.
 * @property width the window width in pixels.
 * @property height the window height in pixels.
 * @property language the language of the game.
 * @property desktop whether to build for desktop.
 * @property html5 whether to build for the web.
 * @property android whether to build for Android.
 * @property flixelVersion the framework version as typed; blank means the latest release on Maven Central.
 * @property template the template to start from.
 */
data class NewProjectSpec(
  val dir: Path,
  val title: String,
  val company: String,
  val version: String,
  val pkg: String,
  val width: Int = DEFAULT_WIDTH,
  val height: Int = DEFAULT_HEIGHT,
  val language: GameLanguage = GameLanguage.JAVA,
  val desktop: Boolean = true,
  val html5: Boolean = true,
  val android: Boolean = false,
  val flixelVersion: String = "",
  val template: String = GameTemplates.DEFAULT,
) {

  /** The platform names in the usual order. */
  val platforms: List<String>
    get() = buildList {
      if (desktop) add("desktop")
      if (html5) add("html5")
      if (android) add("android")
    }

  /**
   * The framework version the person typed, without a leading `v` (release tags look like `v0.7.2`, but Maven
   * versions do not), or `null` when the field was left empty.
   */
  val typedFlixelVersion: String?
    get() = flixelVersion.trim().removePrefix("v").takeIf { it.isNotEmpty() }

  /**
   * Builds the data the templates read.
   *
   * @param flixel the framework version to use.
   * @param ktx the version of the Kotlin extension; only used by Kotlin games.
   */
  fun templateData(flixel: String, ktx: String): GameTemplateData = GameTemplateData.of(
    title = title,
    company = company,
    version = version,
    pkg = pkg,
    language = language,
    platforms = platforms,
    flixelVersion = flixel,
    ktxVersion = ktx,
    folder = dir.fileName?.toString() ?: title,
    width = width,
    height = height,
  )

  companion object {
    /** The default window width in pixels. */
    const val DEFAULT_WIDTH = 640

    /** The default window height in pixels. */
    const val DEFAULT_HEIGHT = 360

    /** The default version of a new game. */
    const val DEFAULT_VERSION = "0.1.0"
  }
}

/** Decides whether the target folder of a new project may be used as it is. */
object TargetFolder {

  /** The entries the IDE itself creates in a new project folder before the files are written. */
  private val WIZARD_FILES = setOf(".idea")

  /** Whether [name] is something the IDE puts into a fresh project folder (`.idea` or a module `.iml` file). */
  fun isWizardFile(name: String): Boolean = name in WIZARD_FILES || name.endsWith(".iml")

  /** Whether [entries] holds something besides wizard files, which means the person picked a folder with content. */
  fun hasForeignFiles(entries: List<String>): Boolean = entries.any { !isWizardFile(it) }
}
