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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallProgressTest {

  @Test
  fun downloadHalfwayIsOneAndAHalfStepsOfFive() {
    assertEquals(0.3, InstallProgress.fraction(2, 5, 500, 1000)!!, 1e-9)
  }

  @Test
  fun downloadStartAndEndStayInsideTheDownloadStep() {
    assertEquals(0.2, InstallProgress.fraction(2, 5, 0, 1000)!!, 1e-9)
    assertEquals(0.4, InstallProgress.fraction(2, 5, 1000, 1000)!!, 1e-9)
  }

  @Test
  fun moreBytesThanTheSizeIsClamped() {
    assertEquals(0.4, InstallProgress.fraction(2, 5, 2000, 1000)!!, 1e-9)
  }

  @Test
  fun unknownSizeOrStepGivesNoFraction() {
    assertNull(InstallProgress.fraction(2, 5, 10, 0))
    assertNull(InstallProgress.fraction(2, 5, 10, -1))
    assertNull(InstallProgress.fraction(0, 5, 1, 2))
    assertNull(InstallProgress.fraction(2, 0, 1, 2))
  }

  @Test
  fun summaries() {
    assertEquals("The debugger v1.0.1 was installed.", InstallResult("installed", "v1.0.1", "/d/x").summary())
    assertEquals("The debugger v1.0.1 is already installed.", InstallResult("unchanged", "v1.0.1", "/d/x").summary())
    assertEquals("The debugger was upgraded.", InstallResult("upgraded", "", "/d/x").summary())
    val withNotes = InstallResult("reinstalled", "v2", "/d/x", listOf("A note.")).summary()
    assertTrue(withNotes.endsWith("\nA note."))
  }
}
