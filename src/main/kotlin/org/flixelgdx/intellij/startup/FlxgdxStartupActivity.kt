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
package org.flixelgdx.intellij.startup

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.flixelgdx.intellij.cli.FlxgdxCli
import org.flixelgdx.intellij.cli.FlxgdxNotifier
import org.flixelgdx.intellij.cli.FlxgdxProject

/**
 * Runs when a project opens. If it is a FlixelGDX project (it has a `project.toml` in its root), it checks the
 * installed `flxgdx` in the background and explains the fix when something is wrong.
 */
class FlxgdxStartupActivity : ProjectActivity {

  override suspend fun execute(project: Project) {
    if (!FlxgdxProject.isFlixelProject(project)) return
    val status = withContext(Dispatchers.IO) { service<FlxgdxCli>().status() }
    FlxgdxNotifier.getInstance().notifyCliProblem(project, status)
  }
}
