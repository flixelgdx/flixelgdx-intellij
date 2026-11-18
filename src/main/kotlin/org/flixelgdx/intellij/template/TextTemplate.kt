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

/** Thrown when a template cannot be parsed or reads something [TextTemplate] does not know. */
class TemplateException(message: String) : RuntimeException(message)

/**
 * Renders the plugin's small template syntax, which fills the `.tmpl` files of a new game.
 *
 * Why its own syntax instead of a template library? The game templates only need to print a few values and leave out
 * blocks for some platforms or languages. The IDE's own file templates use Apache Velocity, but only through the IDE,
 * which would keep [GameTemplates] from being tested without it. So this is a tiny, dependency-free reader that fails
 * loudly on anything it does not understand instead of guessing.
 *
 * Think of it as a mail merge: the text is the letter, and each `{{...}}` is a blank that is filled from the values.
 *
 * What it understands:
 *
 * - `{{name}}` prints a value, and `{{name | filter}}` prints it escaped. The filters are `kotlin` (inside a Kotlin
 *   string), `xml` (inside an XML attribute) and `toml` (inside a TOML basic string).
 * - `{{if name}}`, `{{if not name}}`, `{{else}}` and `{{end}}`. A value is true when it is `true`, a non-empty text or a
 *   non-zero number.
 * - `{{-` removes all white space (including line breaks) right before the tag, and `-}}` removes all white space
 *   right after it. This keeps the output tidy while the template stays readable.
 *
 * Example:
 * ```
 * val values = mapOf("title" to "My Game", "kotlin" to false)
 * TextTemplate.render("README.md.tmpl", "# {{title}}{{if kotlin}} (Kotlin){{end}}", values) // "# My Game"
 * ```
 */
object TextTemplate {

  private const val OPEN = "{{"
  private const val CLOSE = "}}"
  private val WHITE_SPACE = Regex("\\s+")
  private val NAME = Regex("[A-Za-z][A-Za-z0-9]*")

  /**
   * Renders [text] with [values].
   *
   * @param name the name of the template, used in error messages.
   * @param text the template text.
   * @param values the values the template reads by name. Each one is a [String], an [Int] or a [Boolean].
   * @return the rendered text.
   * @throws TemplateException when the template is malformed or reads something that [values] does not have.
   */
  fun render(name: String, text: String, values: Map<String, Any>): String {
    val nodes = Parser(name, tokenize(name, text)).parseAll()
    val out = StringBuilder(text.length)
    for (node in nodes) node.write(out, Scope(name, values))
    return out.toString()
  }

  /** Splits the text into plain text and tags, applying the trim markers on the way. */
  private fun tokenize(name: String, text: String): List<Token> {
    val tokens = ArrayList<Token>()
    var pos = 0
    var trimNext = false
    while (pos <= text.length) {
      val open = text.indexOf(OPEN, pos)
      var plain = if (open < 0) text.substring(pos) else text.substring(pos, open)
      if (trimNext) plain = plain.trimStart()
      if (open < 0) {
        if (plain.isNotEmpty()) tokens += Token.Text(plain)
        break
      }
      val close = text.indexOf(CLOSE, open + OPEN.length)
      if (close < 0) throw TemplateException("$name: a tag that starts with {{ is never closed")
      var inner = text.substring(open + OPEN.length, close)
      if (inner.startsWith("- ")) {
        plain = plain.trimEnd()
        inner = inner.substring(2)
      }
      trimNext = inner.endsWith(" -")
      if (trimNext) inner = inner.substring(0, inner.length - 2)
      if (plain.isNotEmpty()) tokens += Token.Text(plain)
      tokens += Token.Tag(inner.trim())
      pos = close + CLOSE.length
    }
    return tokens
  }

  /** The values and the template name, passed down while writing. */
  private class Scope(val name: String, val values: Map<String, Any>) {

    fun value(key: String): Any = values[key] ?: throw TemplateException("$name: there is no value called $key")
  }

  private sealed interface Token {
    data class Text(val text: String) : Token
    data class Tag(val text: String) : Token
  }

  private sealed interface Node {
    fun write(out: StringBuilder, scope: Scope)
  }

  private class TextNode(val text: String) : Node {
    override fun write(out: StringBuilder, scope: Scope) {
      out.append(text)
    }
  }

  private class PrintNode(val key: String, val filter: String?) : Node {
    override fun write(out: StringBuilder, scope: Scope) {
      val text = scope.value(key).toString()
      out.append(
        when (filter) {
          null -> text
          "kotlin" -> Escapes.kotlinString(text)
          "xml" -> Escapes.xmlAttribute(text)
          "toml" -> Escapes.tomlString(text)
          else -> throw TemplateException("${scope.name}: there is no filter called $filter")
        },
      )
    }
  }

  private class IfNode(val key: String, val negate: Boolean, val then: List<Node>, val otherwise: List<Node>) : Node {
    override fun write(out: StringBuilder, scope: Scope) {
      val branch = if (truthy(scope.value(key), scope.name) != negate) then else otherwise
      for (node in branch) node.write(out, scope)
    }
  }

  /** Builds the tree of nodes, matching every `if` with its `else` and `end`. */
  private class Parser(private val name: String, private val tokens: List<Token>) {
    private var index = 0

    fun parseAll(): List<Node> {
      val nodes = parseUntil(setOf())
      if (index < tokens.size) throw TemplateException("$name: unexpected {{${(tokens[index] as Token.Tag).text}}}")
      return nodes
    }

    /** Parses nodes until a tag whose keyword is in [stops], which is left for the caller. */
    private fun parseUntil(stops: Set<String>): List<Node> {
      val nodes = ArrayList<Node>()
      while (index < tokens.size) {
        when (val token = tokens[index]) {
          is Token.Text -> {
            nodes += TextNode(token.text)
            index++
          }
          is Token.Tag -> {
            val words = token.text.split(WHITE_SPACE).filter { it.isNotEmpty() }
            val keyword = words.firstOrNull() ?: throw TemplateException("$name: empty tag {{}}")
            if (keyword in stops) return nodes
            index++
            nodes += when (keyword) {
              "if" -> parseIf(words.drop(1))
              "else", "end" -> throw TemplateException("$name: {{$keyword}} without a matching {{if}}")
              else -> parsePrint(token.text)
            }
          }
        }
      }
      if (stops.isNotEmpty()) throw TemplateException("$name: an {{if}} is never closed with {{end}}")
      return nodes
    }

    private fun parsePrint(text: String): Node {
      val parts = text.split('|').map { it.trim() }
      if (parts.size > 2 || parts.any { !NAME.matches(it) }) throw TemplateException("$name: cannot read {{$text}}")
      return PrintNode(parts[0], parts.getOrNull(1))
    }

    private fun parseIf(condition: List<String>): Node {
      val negate = condition.firstOrNull() == "not"
      val key = (if (negate) condition.drop(1) else condition).singleOrNull()
      if (key == null || !NAME.matches(key)) {
        throw TemplateException("$name: {{if}} needs one value, optionally after not")
      }
      val then = parseUntil(setOf("else", "end"))
      var otherwise = emptyList<Node>()
      if (index < tokens.size && keywordAt(index) == "else") {
        index++
        otherwise = parseUntil(setOf("end"))
      }
      if (index >= tokens.size || keywordAt(index) != "end") {
        throw TemplateException("$name: an {{if}} is never closed with {{end}}")
      }
      index++
      return IfNode(key, negate, then, otherwise)
    }

    private fun keywordAt(i: Int): String? =
      (tokens[i] as? Token.Tag)?.text?.split(WHITE_SPACE)?.firstOrNull { it.isNotEmpty() }
  }

  private fun truthy(value: Any, name: String): Boolean = when (value) {
    is Boolean -> value
    is String -> value.isNotEmpty()
    is Int -> value != 0
    else -> throw TemplateException("$name: cannot use a ${value::class.simpleName} as a condition")
  }
}
