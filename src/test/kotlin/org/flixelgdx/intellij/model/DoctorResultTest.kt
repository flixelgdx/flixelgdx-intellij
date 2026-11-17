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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DoctorResultTest {

  @Test
  fun readsChecksWithWarnings() {
    val result = DoctorResult.parse(Fixtures.result("doctor"))
    assertTrue(result.inProject)
    assertEquals(14, result.checks.size)
    assertEquals(12, result.count(CheckStatus.OK))
    assertEquals(2, result.count(CheckStatus.WARNING))
    assertEquals(0, result.count(CheckStatus.ERROR))
    val warning = result.checks.first { it.id == "platform.android" }
    assertEquals("project", warning.category)
    assertTrue(warning.fix.isNotEmpty())
    assertFalse(warning.fixed)
  }

  @Test
  fun groupsChecksByCategoryInOrder() {
    val groups = DoctorResult.parse(Fixtures.result("doctor")).byCategory()
    assertEquals(listOf("environment", "project"), groups.keys.toList())
    assertEquals(5, groups.getValue("environment").size)
  }

  @Test
  fun readsAnErrorCheck() {
    val result = DoctorResult.parse(Fixtures.result("doctor-errors"))
    assertEquals(1, result.count(CheckStatus.ERROR))
    assertEquals("gradle-wrapper", result.checks.first { it.status == CheckStatus.ERROR }.id)
  }

  @Test
  fun readsRepairedChecks() {
    val result = DoctorResult.parse(Fixtures.result("doctor-fix"))
    assertEquals(1, result.fixedCount)
    val fixed = result.checks.first { it.fixed }
    assertEquals("package.flixelgdx", fixed.id)
    assertEquals(CheckStatus.OK, fixed.status)
  }

  @Test
  fun anUnknownStatusIsShownAsAWarning() {
    assertEquals(CheckStatus.WARNING, CheckStatus.of("strange"))
    assertEquals(CheckStatus.WARNING, CheckStatus.of(null))
  }

  @Test
  fun anEmptyResultHasNoChecks() {
    val result = DoctorResult.parse(null)
    assertTrue(result.checks.isEmpty())
    assertFalse(result.inProject)
  }
}
