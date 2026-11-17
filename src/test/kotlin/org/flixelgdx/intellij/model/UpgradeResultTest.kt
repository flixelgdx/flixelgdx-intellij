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

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpgradeResultTest {

  @Test
  fun readsADryRunWithAnUpdate() {
    val plan = UpgradeResult.parse(Fixtures.result("upgrade-dry"))
    assertEquals("dry_run", plan.action)
    assertFalse(plan.upToDate)
    val update = plan.updates.single()
    assertEquals("flixelgdx", update.pkg)
    assertEquals("v0.7.0", update.from)
    assertEquals("v0.7.2", update.to)
    assertEquals(listOf("r8"), update.removed)
    assertEquals(listOf("gradle/libs.versions.toml", "project.toml"), update.files)
    assertEquals("flixelgdx: v0.7.0 to v0.7.2", update.line())
  }

  @Test
  fun planListsUpdatesAndDrops() {
    val plan = UpgradeResult.parse(Fixtures.result("upgrade-dry")).plan()
    assertEquals(listOf("flixelgdx: v0.7.0 to v0.7.2", "    drops: r8"), plan)
  }

  @Test
  fun readsAnUpToDateResult() {
    val plan = UpgradeResult.parse(Fixtures.result("upgrade"))
    assertEquals("none", plan.action)
    assertTrue(plan.upToDate)
    assertTrue(plan.plan().isEmpty())
  }

  @Test
  fun planMentionsSkippedPackagesAndOldHandbooks() {
    val data = JsonParser.parseString(
      """
      {"action":"dry_run",
       "updates":[{"package":"fw","from":"v1","to":"v2","framework":true,"changes":{"notes":[{"text":"Hi"}]}}],
       "manual":["tiny-lib"],"handbooks":[{"name":"docs","repo":"o/r"}],"warnings":["careful"]}
      """.trimIndent(),
    ).asJsonObject
    val plan = UpgradeResult.parse(data)
    assertEquals(
      listOf("fw: v1 to v2 (framework)", "Skipped (added by hand): tiny-lib", "Handbooks that are behind: docs"),
      plan.plan(),
    )
    assertEquals(listOf("Hi"), plan.notes)
    assertEquals(listOf("careful"), plan.warnings)
  }

  @Test
  fun anEmptyResultIsUpToDate() {
    assertTrue(UpgradeResult.parse(null).upToDate)
  }
}
