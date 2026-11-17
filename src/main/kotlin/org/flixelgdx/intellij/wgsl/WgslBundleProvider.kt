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

import com.intellij.openapi.application.PathManager
import org.jetbrains.plugins.textmate.api.TextMateBundleProvider
import org.jetbrains.plugins.textmate.api.TextMateBundleProvider.PluginBundle
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Gives the TextMate plugin a bundle that highlights `.wgsl` files.
 *
 * The TextMate plugin reads bundles from real folders, but our files live inside the plugin jar. So the first time
 * the IDE asks, the bundle is copied to the IDE system folder (a few kilobytes) and that folder is handed over.
 * Copying again on every start keeps the folder in step with the plugin version.
 *
 * A language plugin that provides its own WGSL file type wins over a TextMate bundle, so this never conflicts with
 * one.
 */
class WgslBundleProvider : TextMateBundleProvider {

  override fun getBundles(): List<PluginBundle> {
    val dir = extract() ?: return emptyList()
    return listOf(PluginBundle(WgslBundle.NAME, dir))
  }

  private fun extract(): Path? {
    val target = PathManager.getSystemDir().resolve("flixelgdx").resolve("textmate").resolve("wgsl")
    return try {
      for (file in WgslBundle.FILES) {
        val stream = WgslBundleProvider::class.java.getResourceAsStream("${WgslBundle.ROOT}/$file") ?: return null
        val out = target.resolve(file)
        Files.createDirectories(out.parent)
        stream.use { Files.copy(it, out, StandardCopyOption.REPLACE_EXISTING) }
      }
      target
    } catch (_: IOException) {
      null
    }
  }
}
