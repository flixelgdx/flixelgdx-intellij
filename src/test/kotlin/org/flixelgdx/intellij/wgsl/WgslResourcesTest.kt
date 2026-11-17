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
package org.flixelgdx.intellij.wgsl

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.xml.parsers.DocumentBuilderFactory

class WgslResourcesTest {

  private fun text(path: String): String {
    val stream = WgslResourcesTest::class.java.getResourceAsStream(path)
    assertNotNull("missing resource $path", stream)
    return stream!!.use { String(it.readAllBytes(), Charsets.UTF_8) }
  }

  /** The template without its comment lines, so the checks look at code only. */
  private fun templateCode(): String =
    text("/fileTemplates/internal/${WgslContract.TEMPLATE}.wgsl.ft").lines()
      .filterNot { it.trim().startsWith("//") }
      .joinToString("\n")

  @Test
  fun bundleFilesArePresent() {
    for (file in WgslBundle.FILES) {
      assertTrue("empty $file", text("${WgslBundle.ROOT}/$file").isNotBlank())
    }
  }

  @Test
  fun grammarParsesAndScopesWgsl() {
    val grammar = JsonParser.parseString(text("${WgslBundle.ROOT}/syntaxes/wgsl.tmLanguage.json")).asJsonObject
    assertEquals("source.wgsl", grammar.get("scopeName").asString)
    assertTrue(grammar.getAsJsonObject("repository").has("keywords"))
  }

  @Test
  fun bundleManifestPointsAtTheGrammar() {
    val manifest = JsonParser.parseString(text("${WgslBundle.ROOT}/package.json")).asJsonObject
    val contributes = manifest.getAsJsonObject("contributes")
    val extensions = contributes.getAsJsonArray("languages")[0].asJsonObject.getAsJsonArray("extensions")
    assertEquals(".wgsl", extensions[0].asString)
    val path = contributes.getAsJsonArray("grammars")[0].asJsonObject.get("path").asString
    assertTrue(WgslBundle.FILES.contains(path.removePrefix("./")))
  }

  @Test
  fun languageConfigurationParses() {
    val config = JsonParser.parseString(text("${WgslBundle.ROOT}/language-configuration.json")).asJsonObject
    assertEquals("//", config.getAsJsonObject("comments").get("lineComment").asString)
  }

  @Test
  fun templateHasTheFragmentEntryPoint() {
    val code = templateCode()
    assertTrue(code.contains("@fragment"))
    assertTrue(code.contains("fn ${WgslContract.FRAGMENT_ENTRY}(in: ${WgslContract.VERTEX_OUTPUT})"))
    assertTrue(code.contains("-> @location(0) vec4f"))
    assertTrue(code.contains("textureSample(${WgslContract.TEXTURE}, ${WgslContract.SAMPLER}, in.uv)"))
  }

  @Test
  fun templateDoesNotRedeclareTheFrameworkNames() {
    val code = templateCode()
    assertFalse(code.contains("@group(${WgslContract.FRAMEWORK_GROUP})"))
    assertFalse(code.contains("var ${WgslContract.TEXTURE}"))
    assertFalse(code.contains("var ${WgslContract.SAMPLER}"))
    assertFalse(code.contains("struct Flixel"))
  }

  @Test
  fun templateBracesAreBalanced() {
    val code = templateCode()
    assertEquals(code.count { it == '{' }, code.count { it == '}' })
    assertEquals(code.count { it == '(' }, code.count { it == ')' })
  }

  @Test
  fun templateIsAscii() {
    assertTrue(text("/fileTemplates/internal/${WgslContract.TEMPLATE}.wgsl.ft").all { it.code < 128 })
  }

  @Test
  fun liveTemplatesFollowTheContract() {
    val xml = text("/liveTemplates/FlixelGDX WGSL.xml")
    val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.byteInputStream())
    val templates = doc.getElementsByTagName("template")
    assertEquals(3, templates.length)
    val values = HashMap<String, String>()
    for (i in 0 until templates.length) {
      val node = templates.item(i)
      values[node.attributes.getNamedItem("name").nodeValue] = node.attributes.getNamedItem("value").nodeValue
    }
    val entry = "fn ${WgslContract.FRAGMENT_ENTRY}(in: ${WgslContract.VERTEX_OUTPUT})"
    assertTrue(values.getValue("flxfrag").contains(entry))
    assertTrue(values.getValue("flxsample").contains("textureSample(${WgslContract.TEXTURE}, ${WgslContract.SAMPLER}"))
    assertTrue(values.getValue("flxuniform").contains("@group(${WgslContract.SHADER_GROUP}) @binding(0) var<uniform>"))
    assertEquals(3, xml.split("FLIXELGDX_WGSL").size - 1)
  }
}
