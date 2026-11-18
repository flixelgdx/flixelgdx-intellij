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

import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiElement
import org.flixelgdx.intellij.template.Escapes
import org.toml.lang.psi.TomlArray
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlKey
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlKeyValueOwner
import org.toml.lang.psi.TomlLiteral
import org.toml.lang.psi.TomlTable
import org.toml.lang.psi.TomlValue

/**
 * One change to the text of a file: replace the characters from [start] to [end] with [text].
 *
 * An insertion has `start == end`, and a deletion has an empty [text].
 */
data class TextEdit(val start: Int, val end: Int, val text: String)

/**
 * Small, careful edits of TOML files, such as `gradle/libs.versions.toml` and `project.toml`.
 *
 * Why not rewrite the whole file? People keep comments and their own layout in these files, and a rewrite would lose
 * them. So the TOML PSI is only used to find where things are, and each change touches the fewest characters it can:
 * one entry line is added or removed, one item is added to or taken out of an array, one value is replaced. Think of
 * it as editing a printed page with a pen instead of retyping it.
 *
 * The functions only plan [TextEdit]s and never change anything themselves; [apply] writes them into a document. They
 * read PSI, so call them inside a read action (or the write action that applies them).
 *
 * Example:
 * ```
 * val bundle = TomlEdits.entry(file, "bundles", "core")?.value as? TomlArray
 * val edit = TomlEdits.appendToArray(bundle!!, "my-library")
 * TomlEdits.apply(document, listOf(edit))
 * ```
 */
object TomlEdits {

  /** Finds the `[name]` table of [file], or `null` when there is none. */
  fun table(file: TomlFile, name: String): TomlTable? =
    file.children.filterIsInstance<TomlTable>().firstOrNull { it.header.key?.let(::keyName) == name }

  /** Finds the entry [key] of the `[table]` table, or a top-level entry when [table] is `null`. */
  fun entry(file: TomlFile, table: String?, key: String): TomlKeyValue? {
    val owner: PsiElement = if (table == null) file else table(file, table) ?: return null
    val entries = if (owner is TomlKeyValueOwner) owner.entries else owner.children.filterIsInstance<TomlKeyValue>()
    return entries.firstOrNull { keyName(it.key) == key }
  }

  /** The entries of the `[table]` table, empty when there is no such table. */
  fun entries(file: TomlFile, table: String): List<TomlKeyValue> = table(file, table)?.entries.orEmpty()

  /** The dotted name of [key], with quotes removed, such as `version.ref`. */
  fun keyName(key: TomlKey): String = key.segments.joinToString(".") { unquote(it.text) }

  /** The text of a string value, or `null` when [value] is not a plain string. */
  fun string(value: TomlValue?): String? {
    val text = (value as? TomlLiteral)?.text ?: return null
    if (text.length < 2) return null
    val quote = text[0]
    if ((quote != '"' && quote != '\'') || text.last() != quote) return null
    return unquote(text)
  }

  /** The string items of [array], skipping anything that is not a plain string. */
  fun strings(array: TomlArray): List<String> = array.elements.mapNotNull(::string)

  /**
   * Plans a new entry line [line] at the end of the `[table]` table.
   *
   * When the table does not exist yet, it is added at the end of the file.
   */
  fun addEntry(file: TomlFile, table: String, line: String): TextEdit {
    val owner = table(file, table)
    if (owner == null) {
      val text = file.text
      val lead = if (text.isEmpty() || text.endsWith("\n")) "" else "\n"
      return TextEdit(text.length, text.length, "$lead\n[$table]\n$line\n")
    }
    val anchor = owner.entries.lastOrNull() ?: owner.header
    val offset = anchor.textRange.endOffset
    return TextEdit(offset, offset, "\n$line")
  }

  /** Plans the removal of the whole line of [entry], including its line break. */
  fun removeEntry(entry: TomlKeyValue): TextEdit {
    val text = entry.containingFile.text
    var start = entry.textRange.startOffset
    while (start > 0 && text[start - 1] != '\n') start--
    var end = entry.textRange.endOffset
    while (end < text.length && text[end] != '\n') end++
    if (end < text.length) end++
    return TextEdit(start, end, "")
  }

  /** Plans replacing the value of [entry] with the string [value]. */
  fun setString(entry: TomlKeyValue, value: String): TextEdit {
    val current = entry.value
    if (current == null) {
      // The entry is written as "key =" with nothing after it, so the value goes at its end.
      val end = entry.textRange.endOffset
      return TextEdit(end, end, " ${quote(value)}")
    }
    return TextEdit(current.textRange.startOffset, current.textRange.endOffset, quote(value))
  }

  /** Plans adding the string [item] at the end of [array], keeping the items that are there. */
  fun appendToArray(array: TomlArray, item: String): TextEdit {
    val last = array.elements.lastOrNull()
    if (last == null) {
      val range = array.textRange
      return TextEdit(range.startOffset, range.endOffset, "[${quote(item)}]")
    }
    val offset = last.textRange.endOffset
    return TextEdit(offset, offset, ", ${quote(item)}")
  }

  /**
   * Plans taking the string [item] out of [array], together with the comma next to it.
   *
   * @return the edit, or `null` when the array does not hold [item].
   */
  fun removeFromArray(array: TomlArray, item: String): TextEdit? {
    val elements = array.elements
    val index = elements.indexOfFirst { string(it) == item }
    if (index < 0) return null
    if (elements.size == 1) return TextEdit(array.textRange.startOffset, array.textRange.endOffset, "[]")
    val element = elements[index]
    return if (index < elements.lastIndex) {
      TextEdit(element.textRange.startOffset, elements[index + 1].textRange.startOffset, "")
    } else {
      TextEdit(elements[index - 1].textRange.endOffset, element.textRange.endOffset, "")
    }
  }

  /** Writes [text] as a TOML basic string, with quotes. */
  fun quote(text: String): String = "\"" + Escapes.tomlString(text) + "\""

  /**
   * Applies [edits] to [document]. The edits must not overlap; they are applied from the end of the file backwards,
   * so earlier offsets stay valid. Call it inside a write action.
   */
  fun apply(document: Document, edits: List<TextEdit>) {
    for (edit in edits.sortedByDescending { it.start }) {
      document.replaceString(edit.start, edit.end, edit.text)
    }
  }

  /** Applies [edits] to plain text, the same way [apply] changes a document. Useful for tests and previews. */
  fun applyTo(text: String, edits: List<TextEdit>): String {
    val sb = StringBuilder(text)
    for (edit in edits.sortedByDescending { it.start }) sb.replace(edit.start, edit.end, edit.text)
    return sb.toString()
  }

  /** Removes the quotes around a key or string and resolves the common escapes of basic strings. */
  private fun unquote(text: String): String {
    if (text.length < 2) return text
    val quote = text[0]
    if ((quote != '"' && quote != '\'') || text.last() != quote) return text
    val inner = text.substring(1, text.length - 1)
    if (quote == '\'') return inner
    return inner.replace("\\\"", "\"").replace("\\\\", "\\")
  }
}
