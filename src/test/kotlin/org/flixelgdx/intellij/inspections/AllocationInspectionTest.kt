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

class AllocationInspectionTest : FrameworkFixtureTest() {

  private val tool = AllocationInspection()

  private fun check(body: String, members: String = ""): List<String> {
    addFramework()
    val source = "import org.flixelgdx.FlixelBasic; import java.util.*;" +
      " class Foo extends FlixelBasic { $members @Override public void update(float elapsed) { $body } }"
    return problems("Foo.java", source, tool)
  }

  fun testFlagsObjectsArraysAndAnonymousClasses() {
    val found = check(
        "Object a = new Object(); int[] b = new int[4]; int[] c = {1, 2}; " +
        "Runnable r = new Runnable() { public void run() {} };")
    assertEquals(listOf("new Object()", "new int[4]", "{1, 2}", "new Runnable() { public void run() {} }"), found)
  }

  fun testFlagsAllocationsInAStateSubclass() {
    addFramework()
    myFixture.addClass(
      "package org.flixelgdx.group; public abstract class FlixelBasicGroup<T> extends org.flixelgdx.FlixelBasic {" +
        " public void update(float e) {} }",
    )
    myFixture.addClass(
      "package org.flixelgdx; public abstract class FlixelState" +
        " extends org.flixelgdx.group.FlixelBasicGroup<FlixelBasic> { public void update(float e) {} }",
    )
    val source = "import org.flixelgdx.FlixelState;" +
      " class PlayState extends FlixelState { @Override public void update(float e) { Object o = new Object(); } }"
    assertEquals(listOf("new Object()"), problems("PlayState.java", source, tool))
  }

  fun testFlagsAllocationsInNestedBlocksAndLoops() {
    val found = check("for (int i = 0; i < 3; i++) { if (i > 1) { while (true) { Object o = new Object(); break; } } }")
    assertEquals(listOf("new Object()"), found)
  }

  fun testFlagsStringConcatenationButNotConstants() {
    val found = check(
        "String s = \"a\" + elapsed; String t = \"a\" + \"b\"; String u = \"x\" + elapsed + \"y\"; " +
        "s += 1;")
    assertEquals(listOf("\"a\" + elapsed", "\"x\" + elapsed + \"y\"", "s += 1"), found)
  }

  fun testFlagsBoxingIntoCollections() {
    val found = check("list.add(5); list.add(Integer.valueOf(5)); int i = 2; list.add(i);",
        "List<Integer> list = new ArrayList<>();")
    assertEquals(listOf("5", "i"), found)
  }

  fun testFlagsCapturingLambdasOnly() {
    val found = check(
      "Runnable a = () -> {}; Runnable b = () -> System.out.println(elapsed); " +
      "Runnable c = () -> count++; Runnable d = this::toString;",
      "int count;",
    )
    assertEquals(listOf("() -> System.out.println(elapsed)", "() -> count++"), found)
  }

  fun testIgnoresThrowAndConstants() {
    val found = check(
        "if (elapsed < 0) throw new IllegalStateException(\"bad\"); String s = \"text\"; " +
        "Thread.State st = Thread.State.NEW;")
    assertEquals(emptyList<String>(), found)
  }

  fun testIgnoresMethodsThatDoNotOverrideTheFrameworkAndOtherNames() {
    addFramework()
    val source = "import org.flixelgdx.FlixelBasic; class Foo extends FlixelBasic {" +
      " public void tick(float e) { new Object(); } public void update(int e) { new Object(); } }" +
      " class Bar { public void update(float e) { new Object(); } }"
    assertEquals(emptyList<String>(), problems("Foo.java", source, tool))
  }

  fun testFlagsDrawOverrideAndInterfaceImplementations() {
    addFramework()
    val source = "import org.flixelgdx.graphics.FlixelBatch; import org.flixelgdx.functional.FlixelUpdatable;" +
      " class A extends org.flixelgdx.FlixelBasic { @Override public void draw(FlixelBatch b) { new Object(); } }" +
      " class B implements FlixelUpdatable { public void update(float e) { new Object(); } }"
    assertEquals(listOf("new Object()", "new Object()"), problems("A.java", source, tool))
  }

  fun testDoesNothingWithoutTheFramework() {
    addInterfaces()
    val source = "import org.flixelgdx.functional.FlixelUpdatable;" +
      " class B implements FlixelUpdatable { public void update(float e) { new Object(); } }"
    assertEquals(emptyList<String>(), problems("B.java", source, tool))
  }

  fun testCanBeSuppressed() {
    addFramework()
    val source = "import org.flixelgdx.FlixelBasic; class Foo extends FlixelBasic {" +
      " @SuppressWarnings(\"FlixelGdxAllocation\") public void update(float e) { new Object(); }" +
      " public void draw(org.flixelgdx.graphics.FlixelBatch b) { new Object(); } }"
    assertEquals(listOf("new Object()"), problems("Foo.java", source, tool))
  }

  fun testMoveToFieldFix() {
    addFramework()
    val before = """
      import org.flixelgdx.FlixelBasic;

      class Foo extends FlixelBasic {
          int count;

          @Override
          public void update(float e) {
              int[] tmp = ne<caret>w int[4];
              count = tmp.length;
          }
      }
    """.trimIndent()
    val after = """
      import org.flixelgdx.FlixelBasic;

      class Foo extends FlixelBasic {
          int count;
          private int[] tmp = new int[4];

          @Override
          public void update(float e) {
              count = tmp.length;
          }
      }
    """.trimIndent()
    fix("Foo.java", before, tool, "Move to a field", after)
  }

  fun testMoveToFieldIsNotOfferedWhenTheInitializerUsesLocals() {
    addFramework()
    myFixture.enableInspections(tool)
    myFixture.configureByText(
      "Foo.java",
      "import org.flixelgdx.FlixelBasic; class Foo extends FlixelBasic {\n" +
        "  @Override public void update(float e) {\n    int[] tmp = ne<caret>w int[(int) e];\n  }\n}",
    )
    myFixture.doHighlighting()
    assertTrue(myFixture.filterAvailableIntentions("Move to a field").isEmpty())
  }
}
