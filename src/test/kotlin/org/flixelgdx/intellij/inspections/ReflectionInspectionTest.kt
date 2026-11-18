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

class ReflectionInspectionTest : FrameworkFixtureTest() {

  private val tool = ReflectionInspection()

  private fun found(body: String): List<String> {
    addFramework()
    return problems("Foo.java",
        "import java.lang.reflect.*; class Foo throws Exception { void m() throws Exception { $body } }", tool)
  }

  fun testFlagsClassLookupsAndReflectTypes() {
    val found = found(
      "Class<?> c = Class.forName(\"x\"); c.getDeclaredMethod(\"a\"); Object o = c.newInstance();" +
        " Method m = c.getMethod(\"b\"); m.invoke(o); Proxy.newProxyInstance(null, null, null);",
    )
    assertEquals(listOf("forName", "getDeclaredMethod", "newInstance", "getMethod", "invoke", "newProxyInstance"),
        found)
  }

  fun testFlagsMethodHandleLookups() {
    val found = found("java.lang.invoke.MethodHandles.lookup().findVirtual(String.class, \"length\", null);")
    assertEquals(listOf("findVirtual"), found)
  }

  fun testIgnoresPlainCode() {
    assertEquals(emptyList<String>(),
        found("Object o = new Object(); o.getClass().getName(); int mod = Modifier.PUBLIC;"))
  }

  fun testDoesNothingWithoutTheFramework() {
    assertEquals(emptyList<String>(), problems("Foo.java",
        "class Foo { void m() throws Exception { Class.forName(\"x\"); } }", tool))
  }

  fun testCanBeSuppressed() {
    addFramework()
    val source =
        "class Foo { @SuppressWarnings(\"FlixelGdxReflection\") void m() throws Exception { Class.forName(\"x\"); " +
        "} }"
    assertEquals(emptyList<String>(), problems("Foo.java", source, tool))
  }
}
