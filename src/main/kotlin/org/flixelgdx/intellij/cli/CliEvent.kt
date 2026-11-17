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
package org.flixelgdx.intellij.cli

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.diagnostic.Logger

/**
 * One line of `flxgdx --json` output, decoded.
 *
 * The protocol is described in the flxgdx documentation (PROTOCOL.md). Fields this plugin does not know are ignored,
 * so newer versions of flxgdx keep working.
 */
sealed interface CliEvent {
  /** A human-readable text. Show it to people, but never branch on it. */
  val message: String

  /** A step of a longer task started. [total] is `null` when the number of steps is not known. */
  data class Progress(override val message: String, val step: Int, val total: Int?) : CliEvent

  /** Extra information that is not the result. [data] may carry details such as a log level. */
  data class Log(override val message: String, val data: JsonObject?) : CliEvent

  /** Something went wrong, but the command carried on. */
  data class Warning(override val message: String, val data: JsonObject?) : CliEvent

  /** The outcome that was asked for. [data] has a command-specific shape. */
  data class Result(override val message: String, val data: JsonObject?) : CliEvent

  /** The command failed. [code] is a stable machine-readable code such as `validation_error`. */
  data class Error(override val message: String, val code: String?, val data: JsonObject?) : CliEvent
}

/** Turns lines of `flxgdx --json` output into [CliEvent]s. */
object CliEventParser {

  private val log = Logger.getInstance(CliEventParser::class.java)

  /**
   * Decodes one output line.
   *
   * @return the event, or `null` when the line is blank, is not a JSON object, or has an unknown type. Anything that
   * is not blank but cannot be used is written to the IDE log.
   */
  fun parse(line: String): CliEvent? {
    val text = line.trim()
    if (text.isEmpty()) return null

    val obj = try {
      JsonParser.parseString(text).takeIf { it.isJsonObject }?.asJsonObject
    } catch (e: Exception) {
      log.warn("Ignoring a flxgdx output line that is not valid JSON: ${text.take(200)}")
      return null
    }
    if (obj == null) {
      log.warn("Ignoring a flxgdx output line that is not a JSON object: ${text.take(200)}")
      return null
    }

    val message = obj.string("message").orEmpty()
    val data = obj.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
    return when (val type = obj.string("type")) {
      "progress" -> CliEvent.Progress(message, obj.int("step") ?: 0, obj.int("total"))
      "log" -> CliEvent.Log(message, data)
      "warning" -> CliEvent.Warning(message, data)
      "result" -> CliEvent.Result(message, data)
      "error" -> CliEvent.Error(message, obj.string("code"), data)
      else -> {
        log.warn("Ignoring a flxgdx event with an unknown type: $type")
        null
      }
    }
  }

  private fun JsonObject.string(key: String): String? =
    get(key)?.takeIf { it.isJsonPrimitive }?.asString

  private fun JsonObject.int(key: String): Int? =
    get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt
}

/** Reads a string from this JSON element's object at [key], or `null`. Handy for result `data` trees. */
fun JsonElement?.stringAt(key: String): String? {
  val obj = this?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
  return obj.get(key)?.takeIf { it.isJsonPrimitive }?.asString
}
