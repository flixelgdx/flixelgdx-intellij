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

class CliVersionTest {

  @Test
  fun parsesPlainAndPrefixedVersions() {
    assertEquals(CliVersion(1, 2, 3), CliVersion.parse("1.2.3"))
    assertEquals(CliVersion(1, 2, 3), CliVersion.parse(" v1.2.3 "))
    assertEquals(CliVersion(2, 0, 0), CliVersion.parse("2"))
    assertEquals(CliVersion(1, 4, 0), CliVersion.parse("1.4"))
  }

  @Test
  fun parsesPreReleaseAndBuildMetadata() {
    assertEquals(CliVersion(1, 0, 0, "rc1"), CliVersion.parse("1.0.0-rc1"))
    assertEquals(CliVersion(1, 0, 0), CliVersion.parse("1.0.0+build5"))
  }

  @Test
  fun rejectsGarbage() {
    assertNull(CliVersion.parse(""))
    assertNull(CliVersion.parse("abc"))
    assertNull(CliVersion.parse("1.x.0"))
    assertNull(CliVersion.parse("99999999999.0.0"))
  }

  @Test
  fun comparesNumericallyNotAsText() {
    assertTrue(CliVersion(1, 10, 0) > CliVersion(1, 9, 0))
    assertTrue(CliVersion(2, 0, 0) > CliVersion(1, 99, 99))
    assertTrue(CliVersion(1, 0, 1) > CliVersion(1, 0, 0))
    assertEquals(0, CliVersion(1, 0, 0).compareTo(CliVersion(1, 0, 0)))
  }

  @Test
  fun preReleaseSortsBelowRelease() {
    assertTrue(CliVersion(1, 0, 0, "rc1") < CliVersion(1, 0, 0))
    assertTrue(CliVersion(1, 0, 0, "rc1") < CliVersion(1, 0, 0, "rc2"))
    assertTrue(CliVersion(1, 0, 0, "rc9") < CliVersion(1, 0, 1))
  }

  @Test
  fun minimumIsOneZeroZero() {
    assertEquals(CliVersion(1, 0, 0), CliVersion.MINIMUM)
    assertEquals("1.0.0-rc1", CliVersion(1, 0, 0, "rc1").toString())
  }
}
