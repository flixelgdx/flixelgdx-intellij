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

/** Derives the names of a new game from its title. */
object GameNames {

  /** The class name used when the title gives no usable identifier. */
  const val FALLBACK_CLASS = "Game"

  private const val SUFFIX = "Game"

  /**
   * Makes the name of the game class from the title: the words of the title joined in PascalCase, ending with `Game`.
   *
   * "My Cool Game" becomes `MyCoolGame` and "Space Race" becomes `SpaceRaceGame`. A title that gives no usable
   * identifier, such as "2048" or "!!!", gives [FALLBACK_CLASS].
   */
  fun className(title: String): String {
    val sb = StringBuilder()
    for (word in title.split(Regex("[^A-Za-z0-9]+"))) {
      if (word.isEmpty()) continue
      sb.append(word[0].uppercaseChar()).append(word, 1, word.length)
    }
    var name = sb.toString()
    if (name.isEmpty() || !name[0].isLetter()) return FALLBACK_CLASS
    if (!name.endsWith(SUFFIX)) name += SUFFIX
    return name
  }

  /**
   * Makes a lowercase file name from the title, with words joined by hyphens, such as `space-race`.
   *
   * A title with no letter or digit gives `game`.
   */
  fun slug(title: String): String {
    val sb = StringBuilder()
    var dash = false
    for (c in title.lowercase()) {
      if (c in 'a'..'z' || c in '0'..'9') {
        if (dash && sb.isNotEmpty()) sb.append('-')
        dash = false
        sb.append(c)
      } else {
        dash = true
      }
    }
    return if (sb.isEmpty()) "game" else sb.toString()
  }
}
