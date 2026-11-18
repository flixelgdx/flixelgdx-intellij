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
package org.flixelgdx.intellij.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

/**
 * The shared base of the inspection tests.
 *
 * It adds small stand-ins for the framework classes the inspections look for (`FlixelBasic`, the update and draw
 * interfaces, `FlixelMath` and the collections), so the tests do not need the real framework on the classpath.
 */
abstract class FrameworkFixtureTest : LightJavaCodeInsightFixtureTestCase() {

  override fun getProjectDescriptor(): LightProjectDescriptor = JAVA_LATEST_WITH_LATEST_JDK

  /** Adds the update and draw interfaces but not `FlixelBasic`, so the module does not count as a FlixelGDX one. */
  protected fun addInterfaces() {
    myFixture.addClass("package org.flixelgdx.graphics; public interface FlixelBatch {}")
    myFixture.addClass("package org.flixelgdx.functional; public interface FlixelUpdatable { void update(float e); }")
    myFixture.addClass(
      "package org.flixelgdx.functional; import org.flixelgdx.graphics.FlixelBatch;" +
        " public interface FlixelDrawable { void draw(FlixelBatch b); }",
    )
  }

  /** Adds the stand-ins for the framework, which turns the module into a FlixelGDX module. */
  protected fun addFramework() {
    addInterfaces()
    myFixture.addClass(
      "package org.flixelgdx; import org.flixelgdx.graphics.FlixelBatch;" +
        " public abstract class FlixelBasic implements org.flixelgdx.functional.FlixelUpdatable," +
        " org.flixelgdx.functional.FlixelDrawable {" +
        " public void update(float e) {} public void draw(FlixelBatch b) {} }",
    )
    myFixture.addClass(
      "package org.flixelgdx.math; public final class FlixelMath {" +
        " public static float clamp(float v, float min, float max) { return v; } }",
    )
    val generic = mapOf(
      "FlixelArray" to "<T>",
      "FlixelSet" to "<T>",
      "FlixelMap" to "<K, V>",
      "FlixelIdentityMap" to "<K, V>",
      "FlixelIntMap" to "<V>",
      "FlixelLongMap" to "<V>",
      "FlixelObjectIntMap" to "<K>",
      "FlixelObjectFloatMap" to "<K>",
      "FlixelIntSet" to "",
      "FlixelLongSet" to "",
      "FlixelIntArray" to "",
      "FlixelFloatArray" to "",
    )
    for ((name, params) in generic) {
      myFixture.addClass(
        "package org.flixelgdx.collections; public class $name$params {" +
          " public $name() {} public $name(int capacity) {}" +
          " public void add(Object o) {} public void add(int i) {} }",
      )
    }
  }

  /** Opens [text] as [fileName], runs [tool] and returns the highlighted source text of each of its problems. */
  protected fun problems(fileName: String, text: String, tool: LocalInspectionTool): List<String> {
    myFixture.enableInspections(tool)
    myFixture.configureByText(fileName, text)
    return myFixture.doHighlighting()
      .filter { it.inspectionToolId == tool.shortName }
      .sortedBy { it.startOffset }
      .map { it.text }
  }

  /** Applies the quick fix called [name] at the `<caret>` of [text] and checks the file against [after]. */
  protected fun fix(fileName: String, text: String, tool: LocalInspectionTool, name: String, after: String) {
    myFixture.enableInspections(tool)
    myFixture.configureByText(fileName, text)
    myFixture.doHighlighting()
    myFixture.launchAction(myFixture.findSingleIntention(name))
    assertEquals(after, myFixture.editor.document.text)
  }
}
