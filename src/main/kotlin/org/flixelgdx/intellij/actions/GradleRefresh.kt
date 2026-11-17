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
package org.flixelgdx.intellij.actions

import com.intellij.openapi.components.Service
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.gradle.service.project.open.linkAndSyncGradleProject
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.nio.file.Path

/**
 * Makes the IDE notice that `flxgdx` changed the build files.
 *
 * Think of it as a messenger who runs after the builder: `flxgdx` edits `project.toml` and the Gradle files on disk,
 * and [refresh] tells the virtual file system about it and asks Gradle to sync again, so the new libraries show up in
 * the modules without a manual sync.
 */
@Service(Service.Level.PROJECT)
class GradleRefresh(private val project: Project, private val scope: CoroutineScope) {

  /**
   * Refreshes the files under [dir] and then syncs the Gradle project that lives there.
   *
   * When the project is already linked, the normal "reload" sync is scheduled. Otherwise (for example a game folder
   * that was opened as plain files) it is linked first, the same way the New Project wizard does it.
   *
   * Call it from any thread; it only schedules work, and the disk refresh runs on a background dispatcher.
   */
  fun refresh(dir: Path) {
    scope.launch {
      refreshVfs(dir)
      val path = dir.toString()
      val linked = GradleSettings.getInstance(project).getLinkedProjectSettings(path) != null
      if (linked) {
        ExternalSystemUtil.refreshProject(path, ImportSpecBuilder(project, GradleConstants.SYSTEM_ID))
      } else {
        linkAndSyncGradleProject(project, path)
      }
    }
  }

  /**
   * Refreshes only the files under [dir], for changes that Gradle does not care about (for example the handbook
   * folder). Call it from any thread; it only schedules work.
   */
  fun refreshFiles(dir: Path) {
    scope.launch { refreshVfs(dir) }
  }

  private suspend fun refreshVfs(dir: Path) {
    withContext(Dispatchers.IO) {
      LocalFileSystem.getInstance().refreshAndFindFileByNioFile(dir)
        ?.let { VfsUtil.markDirtyAndRefresh(false, true, true, it) }
    }
  }
}
