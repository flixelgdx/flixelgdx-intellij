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

class UnsupportedApiInspectionTest : FlixelFixtureTest() {

  private val tool = FlixelUnsupportedApiInspection()
  private val head =
      "import java.util.*; import java.util.concurrent.*; " +
      "class Foo { void m(List<String> list, Deque<String> dq) throws Exception {\n"

  private fun found(body: String): List<String> {
    addFramework()
    return problems("Foo.java", head + body + "\n} }", tool)
  }

  fun testFlagsMemberCalls() {
    val found = found(
      
          "float f = Math.clamp(1f, 0f, 2f); new StringBuilder().repeat(\"a\", 2); " +
          "long id = Thread.currentThread().threadId();" +
        " Locale l = Locale.of(\"en\");",
    )
    assertEquals(listOf("clamp", "repeat", "threadId", "of"), found)
  }

  fun testFlagsVirtualThreads() {
    val found = found(
        "Thread.ofVirtual(); Thread.startVirtualThread(() -> {}); " +
        "Executors.newVirtualThreadPerTaskExecutor();")
    assertEquals(listOf("ofVirtual", "startVirtualThread", "newVirtualThreadPerTaskExecutor"), found)
  }

  fun testFlagsSequencedMethodsButNotTheOldDequeOnes() {
    val found = found(
        "list.getFirst(); list.getLast(); list.reversed(); list.removeLast(); dq.addFirst(\"a\"); " +
        "dq.getFirst();")
    assertEquals(listOf("getFirst", "getLast", "reversed", "removeLast"), found)
  }

  fun testFlagsTypes() {
    val found = found("HexFormat h = HexFormat.of(); String s = h.formatHex(new byte[0]);")
    assertEquals(listOf("HexFormat", "HexFormat"), found)
  }

  fun testDoesNothingWithoutTheFramework() {
    assertEquals(emptyList<String>(), problems("Foo.java", "class Foo { float f = Math.clamp(1f, 0f, 2f); }", tool))
  }

  fun testCanBeSuppressed() {
    addFramework()
    val source =
        "class Foo { @SuppressWarnings(\"FlixelGdxUnsupportedApi\") float f = Math.clamp(1f, 0f, 2f); " +
        "float g = Math.clamp(1f, 0f, 2f); }"
    assertEquals(listOf("clamp"), problems("Foo.java", source, tool))
  }
}
