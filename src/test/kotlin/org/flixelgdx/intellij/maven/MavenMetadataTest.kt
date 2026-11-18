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
package org.flixelgdx.intellij.maven

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MavenMetadataTest {

  @Test
  fun releaseElementWins() {
    val xml = """
      <metadata><versioning><latest>0.7.2</latest><release>0.7.2</release>
      <versions><version>master</version><version>0.4.0</version><version>0.7.2</version></versions>
      </versioning></metadata>
    """.trimIndent()
    assertEquals("0.7.2", MavenMetadata.latestRelease(xml))
  }

  @Test
  fun highestNumericVersionWhenReleaseIsMissingOrABranch() {
    val xml = "<release>master</release><version>0.9.1</version><version>0.10.0</version><version>dev</version>"
    assertEquals("0.10.0", MavenMetadata.latestRelease(xml))
  }

  @Test
  fun nothingUsable() {
    assertNull(MavenMetadata.latestRelease("<metadata><version>master</version></metadata>"))
  }

  @Test
  fun versionsCompareNumberByNumber() {
    assertTrue(VersionOrder.compare("0.10.0", "0.9.1") > 0)
    assertTrue(VersionOrder.compare("1.0.0-beta", "1.0.0") < 0)
    assertEquals(0, VersionOrder.compare("1.0", "1.0.0"))
  }

  @Test
  fun metadataUrl() {
    assertEquals(
      "https://repo1.maven.org/maven2/org/flixelgdx/flixelgdx-core/maven-metadata.xml",
      MavenCentral.metadataUrl("org.flixelgdx", "flixelgdx-core"),
    )
  }
}
