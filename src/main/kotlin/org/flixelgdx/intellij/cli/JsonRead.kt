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

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/**
 * Small readers for the `data` trees of `flxgdx` results.
 *
 * Every reader is forgiving: a missing key, a `null` value or a value of the wrong type gives an empty answer instead
 * of an exception. That keeps the result models working when a newer `flxgdx` adds or reshapes keys.
 */

/** Reads the string at [key], or `null` when it is missing or not a string. */
fun JsonObject?.str(key: String): String? =
  this?.get(key)?.takeIf { it.isJsonPrimitive }?.asString

/** Reads the boolean at [key]; anything that is not `true` gives `false`. */
fun JsonObject?.flag(key: String): Boolean =
  this?.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean == true

/** Reads the object at [key], or `null`. */
fun JsonObject?.obj(key: String): JsonObject? =
  this?.get(key)?.takeIf { it.isJsonObject }?.asJsonObject

/** Reads the array at [key] and keeps only its objects. A missing key gives an empty list. */
fun JsonObject?.objs(key: String): List<JsonObject> {
  val array: JsonArray = this?.get(key)?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
  return array.filter { it.isJsonObject }.map { it.asJsonObject }
}

/** Reads the array at [key] and keeps only its string items. A missing key gives an empty list. */
fun JsonObject?.strs(key: String): List<String> {
  val array: JsonArray = this?.get(key)?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
  return array.filter { it.isJsonPrimitive }.map { it.asString }
}

/** Reads the whole number at [key], or `null` when it is missing or not a number. */
fun JsonObject?.int(key: String): Int? =
  this?.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt
