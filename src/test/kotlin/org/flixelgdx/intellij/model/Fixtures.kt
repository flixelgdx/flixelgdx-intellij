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
package org.flixelgdx.intellij.model

import com.google.gson.JsonObject
import org.flixelgdx.intellij.cli.CliEvent
import org.flixelgdx.intellij.cli.CliEventParser

/**
 * Loads the recorded `flxgdx --json` output in `src/test/resources/flxgdx`.
 *
 * Each fixture is the real output of one command, with the machine's paths replaced by placeholders.
 */
object Fixtures {

  /** Returns the `data` of the last `result` event in the fixture [name] (without `.json`). */
  fun result(name: String): JsonObject {
    val stream = Fixtures::class.java.getResourceAsStream("/flxgdx/$name.json")
      ?: error("Missing fixture $name")
    val lines = stream.bufferedReader(Charsets.UTF_8).use { it.readLines() }
    val results = lines.mapNotNull { CliEventParser.parse(it) }.filterIsInstance<CliEvent.Result>()
    return results.last().data ?: error("Fixture $name has no data")
  }
}
