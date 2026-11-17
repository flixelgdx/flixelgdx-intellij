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

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.util.Key
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager

/**
 * Tells the inspections whether a file belongs to a module that uses the FlixelGDX framework.
 *
 * The inspections give advice that only makes sense for game code (no garbage per frame, no reflection, only APIs
 * that exist on Android and the web). Running them in an unrelated module would be noise, so each one asks this
 * object first and does nothing when the answer is no.
 *
 * The check looks for the framework base class [BASE_CLASS] in the module's classpath. The answer is cached per
 * module and thrown away when the project's libraries or modules change, so it costs almost nothing per file.
 */
object FlixelScope {

  /** The framework class every game object extends. Its presence means the framework is on the classpath. */
  const val BASE_CLASS = "org.flixelgdx.FlixelBasic"

  private val KEY = Key.create<CachedValue<Boolean>>("org.flixelgdx.intellij.inspections.flixelModule")

  /** Returns true when [file] is in a module that has the FlixelGDX core on its classpath. */
  fun isFlixelFile(file: PsiFile): Boolean {
    val module = ModuleUtilCore.findModuleForPsiElement(file) ?: return false
    return isFlixelModule(module)
  }

  /** Returns true when [module] (including its dependencies) can see [BASE_CLASS]. */
  fun isFlixelModule(module: Module): Boolean {
    val project = module.project
    return CachedValuesManager.getManager(project).getCachedValue(module, KEY, {
      val scope = module.getModuleWithDependenciesAndLibrariesScope(false)
      val found = JavaPsiFacade.getInstance(project).findClass(BASE_CLASS, scope) != null
      CachedValueProvider.Result.create(found, ProjectRootModificationTracker.getInstance(project))
    }, false)
  }
}
