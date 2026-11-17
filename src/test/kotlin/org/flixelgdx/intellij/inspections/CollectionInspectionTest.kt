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

class CollectionInspectionTest : FlixelFixtureTest() {

  private val tool = FlixelCollectionInspection()
  private val head = "import java.util.*; class Foo {\n"

  private fun found(body: String): List<String> {
    addFramework()
    return problems("Foo.java", head + body + "\n}", tool)
  }

  private fun rewrite(declaration: String, name: String, after: String, import: String) {
    addFramework()
    fix("Foo.java", head + declaration + "\n}", tool, name, "import $import;\n\n" + head + after + "\n}")
  }

  fun testFlagsFieldsLocalsParametersAndNewExpressions() {
    val body =
        "List<String> a; void m(Map<String, String> p) { Set<String> s = null; " +
        "Object o = new TreeMap<String, String>(); }" +
      " Object r() { return new ArrayList<String>(); }"
    assertEquals(listOf("List<String>", "Map<String, String>", "Set<String>", "new TreeMap<String, String>()",
        "new ArrayList<String>()"), found(body))
  }

  fun testFlagsDeclarationAndInitializerOnce() {
    assertEquals(listOf("List<String>"), found("List<String> a = new ArrayList<>();"))
  }

  fun testSkipsParametersOfOverridingMethods() {
    addFramework()
    val source = "import java.util.*; import java.util.function.Consumer;" +
      " class Foo implements Consumer<List<String>> { public void accept(List<String> l) {} }"
    assertEquals(emptyList<String>(), problems("Foo.java", source, tool))
  }

  fun testDoesNothingWithoutTheFramework() {
    assertEquals(emptyList<String>(), problems("Foo.java", "import java.util.*; class Foo { List<String> a; }", tool))
  }

  fun testCanBeSuppressed() {
    assertEquals(
      listOf("Map<String, String>"),
      found("@SuppressWarnings(\"FlixelGdxCollection\") List<String> a; Map<String, String> b;"),
    )
  }

  fun testReplacesArrayList() = rewrite(
    "List<St<caret>ring> a = new ArrayList<>();",
    "Replace with FlixelArray",
    "FlixelArray<String> a = new FlixelArray<>();",
    "org.flixelgdx.collections.FlixelArray",
  )

  fun testReplacesHashMapWithIntKeys() = rewrite(
    "private Map<Int<caret>eger, String> a = new HashMap<Integer, String>(32);",
    "Replace with FlixelIntMap",
    "private FlixelIntMap<String> a = new FlixelIntMap<>(32);",
    "org.flixelgdx.collections.FlixelIntMap",
  )

  fun testReplacesBoxedElementTypesWithPrimitiveCollections() = rewrite(
    "ArrayList<Flo<caret>at> a = new ArrayList<>();",
    "Replace with FlixelFloatArray",
    "FlixelFloatArray a = new FlixelFloatArray();",
    "org.flixelgdx.collections.FlixelFloatArray",
  )

  fun testFixIsNotOfferedForOrderedMapsOrCopyConstructors() {
    addFramework()
    myFixture.enableInspections(tool)
    myFixture.configureByText("Foo.java", head + "Map<String, String> <caret>a = new LinkedHashMap<>();\n}")
    myFixture.doHighlighting()
    assertTrue(myFixture.filterAvailableIntentions("Replace with").isEmpty())
    myFixture.configureByText("Foo.java", head + "List<String> <caret>b = new ArrayList<>(Arrays.asList(\"a\"));\n}")
    myFixture.doHighlighting()
    assertTrue(myFixture.filterAvailableIntentions("Replace with").isEmpty())
  }
}
