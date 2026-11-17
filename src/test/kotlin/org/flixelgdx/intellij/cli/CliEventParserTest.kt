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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CliEventParserTest {

  @Test
  fun parsesProgressWithTotal() {
    val e = CliEventParser.parse("""{"protocol":1,"type":"progress","message":"Reading","step":2,"total":5}""")
    assertEquals(CliEvent.Progress("Reading", 2, 5), e)
  }

  @Test
  fun parsesProgressWithoutTotal() {
    val e = CliEventParser.parse("""{"protocol":1,"type":"progress","message":"Working","step":1}""")
    assertEquals(CliEvent.Progress("Working", 1, null), e)
  }

  @Test
  fun parsesLogWithData() {
    val e = CliEventParser.parse("""{"protocol":1,"type":"log","message":"hi","data":{"level":"debug"}}""")
    assertTrue(e is CliEvent.Log)
    assertEquals("debug", (e as CliEvent.Log).data.stringAt("level"))
  }

  @Test
  fun parsesWarning() {
    val e = CliEventParser.parse("""{"protocol":1,"type":"warning","message":"careful"}""")
    assertEquals(CliEvent.Warning("careful", null), e)
  }

  @Test
  fun parsesResultData() {
    val e = CliEventParser.parse("""{"protocol":1,"type":"result","message":"x","data":{"version":"1.0.0"}}""")
    assertTrue(e is CliEvent.Result)
    assertEquals("1.0.0", (e as CliEvent.Result).data.stringAt("version"))
  }

  @Test
  fun parsesError() {
    val e = CliEventParser.parse("""{"protocol":1,"type":"error","message":"bad flag","code":"usage_error"}""")
    assertEquals(CliEvent.Error("bad flag", "usage_error", null), e)
  }

  @Test
  fun ignoresUnknownKeys() {
    val e = CliEventParser.parse("""{"protocol":2,"type":"log","message":"m","future":{"a":1}}""")
    assertEquals(CliEvent.Log("m", null), e)
  }

  @Test
  fun missingMessageBecomesEmpty() {
    assertEquals(CliEvent.Warning("", null), CliEventParser.parse("""{"type":"warning"}"""))
  }

  @Test
  fun blankAndMalformedLinesAreIgnored() {
    assertNull(CliEventParser.parse(""))
    assertNull(CliEventParser.parse("   "))
    assertNull(CliEventParser.parse("not json at all"))
    assertNull(CliEventParser.parse("""{"type":"progress","message":"cut off"""))
    assertNull(CliEventParser.parse("[1,2,3]"))
    assertNull(CliEventParser.parse("42"))
  }

  @Test
  fun unknownTypeIsIgnored() {
    assertNull(CliEventParser.parse("""{"protocol":1,"type":"mystery","message":"?"}"""))
    assertNull(CliEventParser.parse("""{"protocol":1,"message":"no type"}"""))
  }
}
