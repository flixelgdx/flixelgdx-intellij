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
package org.flixelgdx.intellij.game

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/** Recognizes a FlixelGDX game and finds its folder. */
object GameProject {

  /** The file that marks the root of a FlixelGDX game. */
  const val MARKER = "project.toml"

  /** Returns `true` when [dir] holds a `project.toml`. Touches the disk, so call it off the EDT. */
  fun isGameDir(dir: Path): Boolean = Files.isRegularFile(dir.resolve(MARKER))

  /** Returns `true` when the base folder of [project] is a FlixelGDX game. */
  fun isGame(project: Project): Boolean {
    val base = baseDir(project) ?: return false
    return runCatching { isGameDir(base) }.getOrDefault(false)
  }

  /** The Gradle root of [project], or `null` for a project without a base folder. */
  fun baseDir(project: Project): Path? = project.basePath?.let { runCatching { Paths.get(it) }.getOrNull() }

  /** Refreshes the files of [project] and syncs Gradle. See [GradleRefresh]. Call it from any thread. */
  fun refresh(project: Project) {
    baseDir(project)?.let { project.service<GradleRefresh>().refresh(it) }
  }
}
