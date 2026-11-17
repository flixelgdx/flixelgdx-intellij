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
package org.flixelgdx.intellij.debugger

import com.google.gson.JsonParser
import org.flixelgdx.intellij.model.CommandArgs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallProgressTest {

  private fun data(json: String) = JsonParser.parseString(json).asJsonObject

  private fun fraction(step: Int, total: Int?, json: String?) =
    InstallProgress.fraction(step, total, json?.let { data(it) })

  @Test
  fun downloadHalfwayIsOneAndAHalfStepsOfFive() {
    val f = fraction(2, 5, """{"stage":"download","bytes":500,"total":1000}""")
    assertEquals(0.3, f!!, 1e-9)
  }

  @Test
  fun downloadStartAndEndStayInsideTheDownloadStep() {
    assertEquals(0.2, fraction(2, 5, """{"stage":"download","bytes":0,"total":1000}""")!!, 1e-9)
    assertEquals(0.4, fraction(2, 5, """{"stage":"download","bytes":1000,"total":1000}""")!!, 1e-9)
  }

  @Test
  fun moreBytesThanTheTotalIsClamped() {
    assertEquals(0.4, fraction(2, 5, """{"stage":"download","bytes":2000,"total":1000}""")!!, 1e-9)
  }

  @Test
  fun unknownTotalGivesNoFraction() {
    assertNull(fraction(2, 5, """{"stage":"download","bytes":10,"total":0}"""))
  }

  @Test
  fun otherLogEventsAreIgnored() {
    assertNull(fraction(2, 5, """{"stage":"unpack","bytes":10,"total":100}"""))
    assertNull(fraction(2, 5, """{"level":"info"}"""))
    assertNull(fraction(2, 5, null))
    assertNull(fraction(2, 5, """{"stage":"download","bytes":"x","total":100}"""))
  }

  @Test
  fun noStepInformationGivesNoFraction() {
    assertNull(fraction(0, 5, """{"stage":"download","bytes":1,"total":2}"""))
    assertNull(fraction(2, null, """{"stage":"download","bytes":1,"total":2}"""))
  }

  @Test
  fun parsesTheInstallResultAndIgnoresUnknownKeys() {
    val json = """
      {"tool":"debugger","action":"installed","version":"v1.0.1","previous_version":"","dir":"/d",
       "executable":"/d/FlixelDebugger.App","asset":"a.zip","sha256":"x","shortcuts":[],"hints":[],"future":1}
    """.trimIndent()
    val result = InstallResult.parse(data(json))
    assertEquals(InstallResult("installed", "v1.0.1", "/d/FlixelDebugger.App"), result)
    assertEquals("The debugger v1.0.1 was installed.", result.summary())
  }

  @Test
  fun summaryFollowsTheAction() {
    assertTrue(InstallResult("unchanged", "v1", "/x").summary().contains("already installed"))
    assertTrue(InstallResult("upgraded", "v1", "/x").summary().contains("upgraded"))
    assertTrue(InstallResult("reinstalled", "", "/x").summary().startsWith("The debugger was"))
  }

  @Test
  fun installArgumentsAddForceOnlyWhenAsked() {
    assertEquals(listOf("install", "debugger"), CommandArgs.installDebugger(false))
    assertEquals(listOf("install", "debugger", "--force"), CommandArgs.installDebugger(true))
  }
}
