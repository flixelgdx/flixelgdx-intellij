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

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.testFramework.PsiTestUtil
import java.io.File

/**
 * Checks that the inspections also work on Kotlin files, which is what UAST is for.
 *
 * The Kotlin standard library of the test IDE is added to the test module so the Kotlin code resolves.
 */
class KotlinInspectionTest : FlixelFixtureTest() {

  override fun setUp() {
    super.setUp()
    val stdlib = File(PathManager.getHomePath(), "plugins/Kotlin/kotlinc/lib/kotlin-stdlib.jar")
    assertTrue("missing ${stdlib.path}", stdlib.isFile)
    PsiTestUtil.addLibrary(module, stdlib.path)
    addFramework()
  }

  override fun tearDown() {
    try {
      ModuleRootModificationUtil.updateModel(module) { model ->
        model.moduleLibraryTable.libraries.forEach { model.moduleLibraryTable.removeLibrary(it) }
      }
    } finally {
      super.tearDown()
    }
  }

  fun testAllocationInKotlin() {
    val source = """
      import org.flixelgdx.FlixelBasic

      class Foo : FlixelBasic() {
          var count = 0
          override fun update(elapsed: Float) {
              val a = Object()
              val s = "a" + elapsed
              val t = "x ${'$'}elapsed"
              val r = Runnable { count++ }
              val n = intArrayOf(1)
              val ok = "constant"
          }
          fun other() { Object() }
      }
    """.trimIndent()
    assertEquals(
      listOf("Object()", "\"a\" + elapsed", "\"x ${'$'}elapsed\"", "Runnable { count++ }"),
      problems("Foo.kt", source, FlixelAllocationInspection()),
    )
  }

  fun testCollectionsInKotlin() {
    val source = """
      import java.util.ArrayList

      class Foo {
          val a: MutableList<String> = ArrayList()
          val b = mutableListOf<String>()
          val c = hashMapOf<Int, String>()
      }
    """.trimIndent()
    assertEquals(
      listOf("MutableList<String>", "mutableListOf", "hashMapOf"),
      problems("Foo.kt", source, FlixelCollectionInspection()),
    )
  }

  fun testReflectionInKotlin() {
    val source = """
      import kotlin.reflect.full.memberProperties

      class Foo {
          fun m() { Class.forName("x") }
      }
    """.trimIndent()
    assertEquals(
      listOf("import kotlin.reflect.full.memberProperties", "forName"),
      problems("Foo.kt", source, FlixelReflectionInspection()),
    )
  }

  fun testUnsupportedApiInKotlin() {
    val source = """
      class Foo {
          fun m(list: MutableList<String>) {
              list.removeLast()
              Math.clamp(1f, 0f, 2f)
          }
      }
    """.trimIndent()
    assertEquals(listOf("removeLast", "clamp"), problems("Foo.kt", source, FlixelUnsupportedApiInspection()))
  }

  fun testSuppressInKotlin() {
    val source = """
      import org.flixelgdx.FlixelBasic

      class Foo : FlixelBasic() {
          @Suppress("FlixelGdxAllocation")
          override fun update(elapsed: Float) {
              val a = Object()
          }
      }
    """.trimIndent()
    assertEquals(emptyList<String>(), problems("Foo.kt", source, FlixelAllocationInspection()))
  }
}
