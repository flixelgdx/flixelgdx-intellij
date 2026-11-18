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
 * The values a template can read: plain fields such as `{{.Title}}`, and methods with one text argument such as
 * `{{.HasPlatform "android"}}`.
 *
 * A field holds a [String], an [Int] or a [Boolean]. Anything else is a mistake in the plugin and fails the render.
 *
 * @property fields the values read as `{{.Name}}`.
 * @property methods the functions called as `{{.Name "argument"}}`.
 */
data class TemplateValues(
  val fields: Map<String, Any>,
  val methods: Map<String, (String) -> Any> = emptyMap(),
)

/** Thrown when a template cannot be parsed or uses something [GoTemplate] does not know. */
class TemplateException(message: String) : RuntimeException(message)

/**
 * Renders the small part of Go's `text/template` syntax that the game templates use.
 *
 * Why not a full template library? The templates came from the Go version of the project generator, and keeping their
 * syntax means the files stay byte-for-byte comparable with that history. The IDE ships Apache Velocity, but only as a
 * separate platform module with a different syntax, and the templates need very little. So this is a tiny,
 * dependency-free reader that fails loudly on anything it does not understand instead of guessing.
 *
 * What it understands:
 *
 * - `{{.Name}}` prints a field, and `{{.Name "text"}}` calls a method with one text argument.
 * - `{{if PIPELINE}}`, `{{else}}` and `{{end}}`. A value is true when it is `true`, a non-empty text or a non-zero
 *   number.
 * - The functions `not`, `kts` (escape for a Kotlin string), `xml` (escape for an XML attribute) and `toml` (escape
 *   for a TOML basic string), each taking one value.
 * - `{{-` removes all white space (including line breaks) right before the action, and `-}}` removes all white space
 *   right after it, exactly like Go.
 *
 * Think of it as a mail merge: the text is the letter, and each `{{...}}` is a blank that is filled from the values.
 *
 * Example:
 * ```
 * val values = TemplateValues(mapOf("Title" to "My Game", "Kotlin" to false))
 * GoTemplate.render("README.md.tmpl", "# {{.Title}}{{if .Kotlin}} (Kotlin){{end}}", values) // "# My Game"
 * ```
 */
object GoTemplate {

  private const val OPEN = "{{"
  private const val CLOSE = "}}"

  /**
   * Renders [text] with [values].
   *
   * @param name the name of the template, used in error messages.
   * @param text the template text.
   * @param values the fields and methods the template reads.
   * @return the rendered text.
   * @throws TemplateException when the template is malformed or reads something that [values] does not have.
   */
  fun render(name: String, text: String, values: TemplateValues): String {
    val nodes = Parser(name, tokenize(name, text)).parseAll()
    val out = StringBuilder(text.length)
    for (node in nodes) node.write(out, values, name)
    return out.toString()
  }

  /** Splits the text into plain text and actions, applying the trim markers on the way. */
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
      if (close < 0) throw TemplateException("$name: an action that starts with {{ is never closed")
      var inner = text.substring(open + OPEN.length, close)
      if (inner.startsWith("- ")) {
        plain = plain.trimEnd()
        inner = inner.substring(2)
      }
      trimNext = inner.endsWith(" -")
      if (trimNext) inner = inner.substring(0, inner.length - 2)
      if (plain.isNotEmpty()) tokens += Token.Text(plain)
      tokens += Token.Action(inner.trim())
      pos = close + CLOSE.length
    }
    return tokens
  }

  private sealed interface Token {
    data class Text(val text: String) : Token
    data class Action(val text: String) : Token
  }

  private sealed interface Node {
    fun write(out: StringBuilder, values: TemplateValues, name: String)
  }

  private class TextNode(val text: String) : Node {
    override fun write(out: StringBuilder, values: TemplateValues, name: String) {
      out.append(text)
    }
  }

  private class PrintNode(val pipeline: List<String>) : Node {
    override fun write(out: StringBuilder, values: TemplateValues, name: String) {
      out.append(evaluate(pipeline, values, name).toString())
    }
  }

  private class IfNode(val pipeline: List<String>, val then: List<Node>, val otherwise: List<Node>) : Node {
    override fun write(out: StringBuilder, values: TemplateValues, name: String) {
      val branch = if (truthy(evaluate(pipeline, values, name))) then else otherwise
      for (node in branch) node.write(out, values, name)
    }
  }

  /** Builds the tree of nodes, matching every `if` with its `else` and `end`. */
  private class Parser(private val name: String, private val tokens: List<Token>) {
    private var index = 0

    fun parseAll(): List<Node> {
      val nodes = parseUntil(setOf())
      if (index < tokens.size) throw TemplateException("$name: unexpected {{${(tokens[index] as Token.Action).text}}}")
      return nodes
    }

    /** Parses nodes until an action whose keyword is in [stops], which is left for the caller. */
    private fun parseUntil(stops: Set<String>): List<Node> {
      val nodes = ArrayList<Node>()
      while (index < tokens.size) {
        when (val token = tokens[index]) {
          is Token.Text -> {
            nodes += TextNode(token.text)
            index++
          }
          is Token.Action -> {
            val words = split(token.text)
            val keyword = words.firstOrNull() ?: throw TemplateException("$name: empty action {{}}")
            if (keyword in stops) return nodes
            index++
            nodes += when (keyword) {
              "if" -> parseIf(words.drop(1))
              "else", "end" -> throw TemplateException("$name: {{$keyword}} without a matching {{if}}")
              "range", "with", "define", "template", "block" ->
                throw TemplateException("$name: {{$keyword}} is not supported")
              else -> PrintNode(words)
            }
          }
        }
      }
      if (stops.isNotEmpty()) throw TemplateException("$name: an {{if}} is never closed with {{end}}")
      return nodes
    }

    private fun parseIf(pipeline: List<String>): Node {
      if (pipeline.isEmpty()) throw TemplateException("$name: {{if}} needs a condition")
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
      return IfNode(pipeline, then, otherwise)
    }

    private fun keywordAt(i: Int): String? = (tokens[i] as? Token.Action)?.let { split(it.text).firstOrNull() }
  }

  /** Splits an action into words, keeping a double-quoted text as one word with its quotes. */
  private fun split(text: String): List<String> {
    val words = ArrayList<String>()
    var i = 0
    while (i < text.length) {
      val c = text[i]
      when {
        c.isWhitespace() -> i++
        c == '"' -> {
          val end = text.indexOf('"', i + 1)
          if (end < 0) throw TemplateException("a quoted text in {{$text}} is never closed")
          words += text.substring(i, end + 1)
          i = end + 1
        }
        else -> {
          var end = i
          while (end < text.length && !text[end].isWhitespace()) end++
          words += text.substring(i, end)
          i = end
        }
      }
    }
    return words
  }

  /**
   * Works out the value of a pipeline: a field, a method call (`.Name "arg"`), or a function call (`not .Kotlin`).
   * Function calls nest from the right, so `not .HasPlatform "x"` is `not (.HasPlatform "x")`.
   */
  private fun evaluate(words: List<String>, values: TemplateValues, name: String): Any {
    val head = words.first()
    if (head.startsWith(".")) {
      val key = head.substring(1)
      if (words.size == 1) {
        return values.fields[key] ?: throw TemplateException("$name: there is no field .$key")
      }
      val method = values.methods[key] ?: throw TemplateException("$name: there is no method .$key")
      if (words.size != 2) throw TemplateException("$name: .$key takes exactly one argument")
      return method(literal(words[1], name))
    }
    if (words.size < 2) throw TemplateException("$name: the function $head needs an argument")
    val arg = evaluate(words.drop(1), values, name)
    return when (head) {
      "not" -> !truthy(arg)
      "kts" -> Escapes.kotlinString(arg.toString())
      "xml" -> Escapes.xmlAttribute(arg.toString())
      "toml" -> Escapes.tomlString(arg.toString())
      else -> throw TemplateException("$name: there is no function $head")
    }
  }

  private fun literal(word: String, name: String): String {
    if (word.length < 2 || !word.startsWith('"') || !word.endsWith('"')) {
      throw TemplateException("$name: expected a quoted text, not $word")
    }
    return word.substring(1, word.length - 1)
  }

  private fun truthy(value: Any): Boolean = when (value) {
    is Boolean -> value
    is String -> value.isNotEmpty()
    is Int -> value != 0
    else -> throw TemplateException("cannot use a ${value::class.simpleName} as a condition")
  }
}

/** Escapes text for the places a template puts it. */
object Escapes {

  /** Escapes [text] so it can sit inside a double-quoted Kotlin string. */
  fun kotlinString(text: String): String = buildString(text.length) {
    for (c in text) {
      when (c) {
        '\\' -> append("\\\\")
        '"' -> append("\\\"")
        '$' -> append("\\$")
        '\n' -> append("\\n")
        '\r' -> append("\\r")
        else -> append(c)
      }
    }
  }

  /** Escapes [text] so it can sit inside an XML attribute. */
  fun xmlAttribute(text: String): String = buildString(text.length) {
    for (c in text) {
      when (c) {
        '&' -> append("&amp;")
        '<' -> append("&lt;")
        '>' -> append("&gt;")
        '"' -> append("&quot;")
        '\'' -> append("&apos;")
        else -> append(c)
      }
    }
  }

  /** Escapes [text] so it can sit inside a double-quoted TOML string. */
  fun tomlString(text: String): String = buildString(text.length) {
    for (c in text) {
      when {
        c == '\\' -> append("\\\\")
        c == '"' -> append("\\\"")
        c == '\n' -> append("\\n")
        c == '\r' -> append("\\r")
        c == '\t' -> append("\\t")
        c < ' ' -> append("\\u%04X".format(c.code))
        else -> append(c)
      }
    }
  }
}
