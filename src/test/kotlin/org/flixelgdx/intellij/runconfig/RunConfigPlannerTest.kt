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
package org.flixelgdx.intellij.runconfig

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunConfigPlannerTest {

  private val desktopWeb = listOf("desktop", "html5")

  private fun tracked(vararg specs: RunSpec) = specs.map { RunTracked(it.platform, it.name, it.task) }

  @Test
  fun mapsPlatformsToConfigurationsInAFixedOrder() {
    val names = RunConfigPlanner.desired(listOf("android", "html5", "desktop", "ios")).map { it.name }
    assertEquals(listOf("Desktop", "Desktop (Debug Tools)", "Web", "Web (Debug Tools)", "Android"), names)
    val tasks = RunConfigPlanner.desired(listOf("android", "html5", "desktop")).map { it.task }
    assertEquals(
      listOf(":desktop:run", ":desktop:debug", ":html5:run", ":html5:debug", ":android:installDebug"),
      tasks,
    )
  }

  @Test
  fun createsEverythingForAFreshProject() {
    val plan = RunConfigPlanner.plan(desktopWeb, emptyList(), emptyList())
    assertEquals(4, plan.create.size)
    assertTrue(plan.remove.isEmpty())
    assertEquals(plan.create.map { it.name }, plan.tracked.map { it.name })
  }

  @Test
  fun createsOnlyWhatIsMissing() {
    val existing = listOf(RunExisting("Desktop", listOf(":desktop:run")))
    val plan = RunConfigPlanner.plan(listOf("desktop"), existing, tracked(RunConfigPlanner.specs("desktop")[0]))
    assertEquals(listOf("Desktop (Debug Tools)"), plan.create.map { it.name })
  }

  @Test
  fun keepsAConfigurationTheUserEditedUnderTheSameName() {
    val existing = listOf(RunExisting("Desktop", listOf(":desktop:run", "--info")))
    val plan = RunConfigPlanner.plan(listOf("desktop"), existing, emptyList())
    assertEquals(listOf("Desktop (Debug Tools)"), plan.create.map { it.name })
  }

  @Test
  fun doesNotDuplicateAUserConfigurationThatRunsTheSameTask() {
    val existing = listOf(RunExisting("My game", listOf(":desktop:run")))
    val plan = RunConfigPlanner.plan(listOf("desktop"), existing, emptyList())
    assertEquals(listOf("Desktop (Debug Tools)"), plan.create.map { it.name })
  }

  @Test
  fun doesNotBringBackAConfigurationTheUserDeleted() {
    val specs = RunConfigPlanner.specs("desktop")
    val plan = RunConfigPlanner.plan(listOf("desktop"), emptyList(), tracked(*specs.toTypedArray()))
    assertTrue(plan.isEmpty)
    assertEquals(2, plan.tracked.size)
  }

  @Test
  fun removesOnlyUntouchedConfigurationsOfARemovedPlatform() {
    val web = RunConfigPlanner.specs("html5")
    val existing = listOf(
      RunExisting("Desktop", listOf(":desktop:run")),
      RunExisting("Web", listOf(":html5:run")),
      RunExisting("Web (Debug Tools)", listOf(":html5:debug", "--info")),
    )
    val known = tracked(RunConfigPlanner.specs("desktop")[0], web[0], web[1])
    val plan = RunConfigPlanner.plan(listOf("desktop"), existing, known)
    assertEquals(listOf("Web"), plan.remove.map { it.name })
    assertEquals(listOf("Desktop", "Desktop (Debug Tools)"), plan.tracked.map { it.name })
    assertTrue(plan.create.map { it.name }.contains("Desktop (Debug Tools)"))
  }

  @Test
  fun neverRemovesConfigurationsThePluginDidNotCreate() {
    val existing = listOf(RunExisting("Web", listOf(":html5:run")))
    val plan = RunConfigPlanner.plan(listOf("desktop"), existing, emptyList())
    assertTrue(plan.remove.isEmpty())
  }

  @Test
  fun selectsDesktopFirstWhenItIsEnabled() {
    assertEquals("Desktop", RunConfigPlanner.selectionName(listOf("html5", "desktop")))
    assertEquals("Web", RunConfigPlanner.selectionName(listOf("html5")))
    assertNull(RunConfigPlanner.selectionName(emptyList()))
  }

  @Test
  fun noPlatformsMeansNothingToCreate() {
    assertTrue(RunConfigPlanner.plan(emptyList(), emptyList(), emptyList()).isEmpty)
  }

  @Test
  fun debugToolsConfigurationsGetTheDebuggerStepWhenItIsInstalled() {
    val plan = RunConfigPlanner.plan(desktopWeb, emptyList(), emptyList(), debuggerInstalled = true)
    assertEquals(
      mapOf(
        "Desktop" to false,
        "Desktop (Debug Tools)" to true,
        "Web" to false,
        "Web (Debug Tools)" to true,
      ),
      plan.create.associate { it.name to it.openDebugger },
    )
  }

  @Test
  fun noDebuggerStepWhenTheDebuggerIsNotInstalled() {
    val plan = RunConfigPlanner.plan(desktopWeb, emptyList(), emptyList())
    assertTrue(plan.create.none { it.openDebugger })
  }

  @Test
  fun existingConfigurationsNeverGetTheDebuggerStep() {
    val existing = listOf(RunExisting("Desktop (Debug Tools)", listOf(":desktop:debug", "--info")))
    val plan = RunConfigPlanner.plan(listOf("desktop"), existing, emptyList(), debuggerInstalled = true)
    assertEquals(listOf("Desktop"), plan.create.map { it.name })
    assertTrue(plan.create.none { it.openDebugger })
  }

  @Test
  fun aUserConfigurationOnTheDebugTaskIsLeftAlone() {
    val existing = listOf(RunExisting("My debug", listOf(":desktop:debug")))
    val plan = RunConfigPlanner.plan(listOf("desktop"), existing, emptyList(), debuggerInstalled = true)
    assertTrue(plan.create.none { it.task == ":desktop:debug" })
  }

  @Test
  fun aDeletedDebugConfigurationIsNotBroughtBackWithTheStep() {
    val specs = RunConfigPlanner.specs("desktop")
    val plan = RunConfigPlanner.plan(listOf("desktop"), emptyList(), tracked(*specs.toTypedArray()), true)
    assertTrue(plan.isEmpty)
  }

  @Test
  fun recognizesTheDebugToolsTasks() {
    assertTrue(RunConfigPlanner.isDebugTools(listOf(":desktop:debug")))
    assertTrue(RunConfigPlanner.isDebugTools(listOf(":html5:debug", "--info")))
    assertFalse(RunConfigPlanner.isDebugTools(listOf(":desktop:run")))
    assertFalse(RunConfigPlanner.isDebugTools(listOf(":android:installDebug")))
    assertFalse(RunConfigPlanner.isDebugTools(emptyList()))
  }
}
