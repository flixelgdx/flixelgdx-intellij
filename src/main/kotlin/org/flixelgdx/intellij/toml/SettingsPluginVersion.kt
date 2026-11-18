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

/**
 * Finds and changes the version of the FlixelGDX settings plugin in `settings.gradle.kts`.
 *
 * The settings plugin (`org.flixelgdx.project`) reads `project.toml` and must match the framework version, so an
 * upgrade changes both. The line looks like `id("org.flixelgdx.project") version "0.7.2"`. Only the version text is
 * replaced; the rest of the file is left exactly as it is.
 */
object SettingsPluginVersion {

  /** Where the settings file lives, relative to the project folder. */
  const val PATH = "settings.gradle.kts"

  /** The id of the Gradle settings plugin of the framework. */
  const val PLUGIN_ID = "org.flixelgdx.project"

  private val LINE = Regex("""id\(\s*"${Regex.escape(PLUGIN_ID)}"\s*\)\s*version\s*"([^"]*)"""")

  /** The version written in [text], or `null` when the plugin line is missing or has no version. */
  fun find(text: String): String? = LINE.find(text)?.groupValues?.get(1)

  /** Plans replacing the version in [text] with [version], or returns `null` when there is no plugin line. */
  fun edit(text: String, version: String): TextEdit? {
    val group = LINE.find(text)?.groups?.get(1) ?: return null
    return TextEdit(group.range.first, group.range.last + 1, version)
  }
}
