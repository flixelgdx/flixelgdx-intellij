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

import java.nio.file.Path

/** The language a new game is written in. [flag] is the value of `flxgdx new --language`. */
enum class GameLanguage(val flag: String) {
  JAVA("java"),
  KOTLIN("kotlin"),
}

/**
 * Everything the New Project wizard collects, one field per `flxgdx new` flag.
 *
 * It is plain data on purpose: the wizard fills it in, [toArgs] turns it into the exact argument list, and tests can
 * check that mapping without any UI. The title, company and version are expected to be trimmed already (the wizard
 * trims them, the same as `flxgdx` does).
 *
 * Example:
 * ```
 * val args = NewProjectSpec(dir, "My Game", "My Studio", "0.1.0", "com.mystudio.mygame").toArgs()
 * service<FlxgdxRunner>().run(args, workDir = dir.parent)
 * ```
 *
 * @property dir the folder of the new project (it becomes the `<dir>` argument).
 * @property title the name of the game (`--title`).
 * @property company the studio or person (`--company`).
 * @property version the game version (`--app-version`).
 * @property pkg the Java package (`--package`).
 * @property width the window width in pixels (`--width`).
 * @property height the window height in pixels (`--height`).
 * @property language the language of the game (`--language`).
 * @property desktop whether to build for desktop (`--platforms`).
 * @property html5 whether to build for the web (`--platforms`).
 * @property android whether to build for Android (`--platforms`).
 * @property libraries comma-separated library repositories (`--libraries`); blank leaves the flag out.
 * @property plugins comma-separated Gradle plugin repositories (`--plugins`); blank leaves the flag out.
 * @property suggested whether to install the packages that the chosen packages suggest (off adds `--no-suggested`).
 * @property handbook whether to install the framework handbook (`--handbook`).
 * @property git whether to run `git init` in the project (`--git`).
 * @property flixelVersion the framework release tag (`--flixel-version`); blank means the latest release.
 * @property template the template to start from (`--template`).
 * @property force whether to allow a folder that already holds the wizard's own files (`--force`).
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
  val libraries: String = "",
  val plugins: String = "",
  val suggested: Boolean = true,
  val handbook: Boolean = true,
  val git: Boolean = false,
  val flixelVersion: String = "",
  val template: String = DEFAULT_TEMPLATE,
  val force: Boolean = false,
) {

  /** The platform names in the order `flxgdx` lists them. */
  val platforms: List<String>
    get() = buildList {
      if (desktop) add("desktop")
      if (html5) add("html5")
      if (android) add("android")
    }

  /**
   * Builds the arguments of `flxgdx new`, without `--json` and `--no-input` (the runner adds those).
   *
   * Values the wizard always knows are always passed, so the result never depends on defaults of the tool. Optional
   * flags appear only when they are switched on (or, for `--no-suggested`, switched off).
   */
  fun toArgs(): List<String> = buildList {
    add("new")
    add(dir.toString())
    add("--title")
    add(title)
    add("--company")
    add(company)
    add("--app-version")
    add(version)
    add("--package")
    add(pkg)
    add("--width")
    add(width.toString())
    add("--height")
    add(height.toString())
    add("--platforms")
    add(platforms.joinToString(","))
    add("--language")
    add(language.flag)
    add("--template")
    add(template)

    val libs = RepoSpecs.normalize(libraries)
    if (libs.isNotEmpty()) {
      add("--libraries")
      add(libs.joinToString(","))
    }
    val plug = RepoSpecs.normalize(plugins)
    if (plug.isNotEmpty()) {
      add("--plugins")
      add(plug.joinToString(","))
    }
    if (flixelVersion.isNotBlank()) {
      add("--flixel-version")
      add(flixelVersion.trim())
    }
    if (!suggested) add("--no-suggested")
    if (handbook) add("--handbook")
    if (git) add("--git")
    if (force) add("--force")
  }

  companion object {
    /** The default window width in pixels, the same as `flxgdx new`. */
    const val DEFAULT_WIDTH = 640

    /** The default window height in pixels, the same as `flxgdx new`. */
    const val DEFAULT_HEIGHT = 360

    /** The default version of a new game. */
    const val DEFAULT_VERSION = "0.1.0"

    /** The template used when none is chosen. */
    const val DEFAULT_TEMPLATE = "blank"

    /**
     * The templates the wizard offers. More templates will be listed here as the `flxgdx` tool grows them; the
     * wizard passes the chosen name through and lets `flxgdx` decide whether it exists.
     */
    val TEMPLATES = listOf(DEFAULT_TEMPLATE)
  }
}

/** Reads the comma-separated repository fields of the wizard. */
object RepoSpecs {

  /** Splits [text] at commas, trims every entry and drops empty ones. */
  fun normalize(text: String): List<String> = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }

  /**
   * Finds the first entry of [text] that cannot be a repository, so the wizard can say so before running `flxgdx`.
   *
   * The check is deliberately loose, because `flxgdx` accepts `owner/repo`, URLs and local folders. It only rejects
   * what can never work: an entry that ends with a colon (a package name is missing) or contains white space.
   *
   * @return a sentence describing the problem, or `null` when every entry looks usable.
   */
  fun problem(text: String): String? {
    for (entry in normalize(text)) {
      if (entry.endsWith(":")) return "'$entry' ends with ':' but has no package name."
      if (entry.any { it.isWhitespace() }) return "'$entry' must not contain spaces. Separate entries with commas."
    }
    return null
  }
}

/** Decides whether the target folder of a new project may be used as it is. */
object TargetFolder {

  /**
   * The entries the wizard itself creates in a new project folder before `flxgdx` runs. A folder that holds only
   * these counts as empty.
   */
  private val WIZARD_FILES = setOf(".idea")

  /** Whether [name] is something the IDE puts into a fresh project folder (`.idea` or a module `.iml` file). */
  fun isWizardFile(name: String): Boolean = name in WIZARD_FILES || name.endsWith(".iml")

  /**
   * Whether `flxgdx new` needs `--force` for a folder holding [entries].
   *
   * `flxgdx` refuses any folder that is not empty. The wizard creates the folder and opens it as a project first, so
   * it may already hold `.idea`. Forcing is only needed (and only allowed) when every entry is such a wizard file.
   */
  fun needsForce(entries: List<String>): Boolean = entries.isNotEmpty() && entries.all(::isWizardFile)

  /** Whether [entries] holds something besides wizard files, which means the person picked a folder with content. */
  fun hasForeignFiles(entries: List<String>): Boolean = entries.any { !isWizardFile(it) }
}
