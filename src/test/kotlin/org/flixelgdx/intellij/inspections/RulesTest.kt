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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesTest {

  @Test
  fun mapsListsToFlixelArrayOrPrimitiveArrays() {
    val list = "java.util.List"
    val arrayList = "java.util.ArrayList"
    assertEquals(Target("FlixelArray", listOf(0)), CollectionMapping.target(list, arrayList,
        listOf("java.lang.String")))
    assertEquals(Target("FlixelIntArray", emptyList()), CollectionMapping.target(arrayList, arrayList,
        listOf("java.lang.Integer")))
    assertEquals(Target("FlixelCharArray", emptyList()), CollectionMapping.target(list, arrayList,
        listOf("java.lang.Character")))
    assertEquals(Target("FlixelArray", listOf(0)), CollectionMapping.target(list, arrayList,
        listOf("java.lang.Double")))
  }

  @Test
  fun mapsMapsAndSetsByKeyAndValueType() {
    val map = "java.util.Map"
    val hashMap = "java.util.HashMap"
    assertEquals(Target("FlixelMap", listOf(0, 1)), CollectionMapping.target(map, hashMap, listOf("java.lang.String",
        "java.lang.Object")))
    assertEquals(Target("FlixelIntMap", listOf(1)), CollectionMapping.target(map, hashMap, listOf("java.lang.Integer",
        "x.V")))
    assertEquals(Target("FlixelLongMap", listOf(1)), CollectionMapping.target(map, hashMap, listOf("java.lang.Long",
        "x.V")))
    assertEquals(Target("FlixelObjectIntMap", listOf(0)), CollectionMapping.target(map, hashMap, listOf("x.K",
        "java.lang.Integer")))
    assertEquals(Target("FlixelObjectFloatMap", listOf(0)), CollectionMapping.target(map, hashMap, listOf("x.K",
        "java.lang.Float")))
    assertEquals(Target("FlixelIntSet", emptyList()), CollectionMapping.target("java.util.Set", "java.util.HashSet",
        listOf("java.lang.Integer")))
    assertEquals(Target("FlixelSet", listOf(0)), CollectionMapping.target("java.util.HashSet", "java.util.HashSet",
        listOf("x.T")))
    assertEquals(
      Target("FlixelIdentityMap", listOf(0, 1)),
      CollectionMapping.target(map, "java.util.IdentityHashMap", listOf("x.K", "x.V")),
    )
  }

  @Test
  fun refusesWhenTheBehaviorWouldChange() {
    assertNull(CollectionMapping.target("java.util.Map", "java.util.LinkedHashMap", listOf("a.K", "a.V")))
    assertNull(CollectionMapping.target("java.util.TreeMap", "java.util.TreeMap", listOf("a.K", "a.V")))
    assertNull(CollectionMapping.target("java.util.Collection", "java.util.ArrayList", listOf("a.T")))
    assertNull(CollectionMapping.target("java.util.List", "java.util.LinkedList", listOf("a.T")))
    assertNull(CollectionMapping.target("java.util.List", "java.util.ArrayList", emptyList()))
  }

  @Test
  fun suggestsTheClosestClass() {
    assertEquals("FlixelMap", CollectionMapping.suggestion("java.util.TreeMap"))
    assertEquals("FlixelSet", CollectionMapping.suggestion("java.util.LinkedHashSet"))
    assertEquals("FlixelArray", CollectionMapping.suggestion("java.util.ArrayDeque"))
    assertEquals("FlixelIdentityMap", CollectionMapping.suggestion("java.util.IdentityHashMap"))
  }

  @Test
  fun recognizesReflection() {
    assertTrue(ReflectionRules.isReflection("java.lang.Class", "forName"))
    assertTrue(ReflectionRules.isReflection("java.lang.reflect.Method", "invoke"))
    assertTrue(ReflectionRules.isReflection("java.lang.invoke.MethodHandles.Lookup", "findStatic"))
    assertTrue(ReflectionRules.isReflection("kotlin.reflect.full.KClasses", "getMemberProperties"))
    assertFalse(ReflectionRules.isReflection("java.lang.Class", "getName"))
    assertFalse(ReflectionRules.isReflection("java.lang.reflect.Modifier", "isStatic"))
    assertTrue(ReflectionRules.isReflectionImport("kotlin.reflect.full.memberProperties"))
    assertTrue(ReflectionRules.isReflectionImport("kotlin.reflect.full"))
    assertFalse(ReflectionRules.isReflectionImport("kotlin.reflect.KClass"))
  }

  @Test
  fun matchesTheUnsupportedApiList() {
    assertNotNull(UnsupportedApis.forClass("java.lang.foreign.Arena"))
    assertNotNull(UnsupportedApis.forClass("java.lang.classfile.ClassFile"))
    assertNotNull(UnsupportedApis.forClass("java.util.HexFormat"))
    assertNull(UnsupportedApis.forClass("java.util.List"))
    assertNotNull(UnsupportedApis.forMethod("java.lang.Math", "clamp"))
    assertNull(UnsupportedApis.forMethod("java.lang.Math", "max"))
    assertNotNull(UnsupportedApis.forMethod("java.util.List", "getFirst"))
    assertNull(UnsupportedApis.forMethod("java.util.Deque", "getFirst"))
    assertNotNull(UnsupportedApis.forMethod("java.util.Deque", "reversed"))
  }

  @Test
  fun buildsMessagesWithAReplacement() {
    val rule = UnsupportedApis.forMethod("java.lang.Math", "clamp")!!
    assertTrue(UnsupportedApis.message(rule, "Math.clamp", "clamp").contains("FlixelMath.clamp"))
    val first = UnsupportedApis.forMethod("java.util.List", "getFirst")!!
    assertTrue(UnsupportedApis.message(first, "List.getFirst", "getFirst").contains("get(0)"))
  }
}
