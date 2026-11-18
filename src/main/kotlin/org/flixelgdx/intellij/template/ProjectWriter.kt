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
package org.flixelgdx.intellij.template

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission

/**
 * Writes rendered [GameFile]s to disk, and takes them away again when something goes wrong.
 *
 * Think of it as a careful mover: it writes every box, and if one does not fit, it carries every box it already
 * brought back out, so the folder is left as it was found. Only the files and folders it created are removed.
 *
 * It touches the disk, so call it off the EDT.
 */
object ProjectWriter {

  /**
   * Writes [files] below [dir].
   *
   * @param overwrite whether an existing file may be replaced. When `false`, existing files are skipped and left
   * untouched, which is what adding a platform module wants.
   * @return the paths of the files that were written.
   * @throws IOException when a file cannot be written. Everything this call created is removed first.
   */
  fun write(dir: Path, files: List<GameFile>, overwrite: Boolean = true): List<Path> {
    val created = ArrayList<Path>()
    val written = ArrayList<Path>()
    try {
      if (!Files.isDirectory(dir)) {
        Files.createDirectories(dir)
        created.add(dir)
      }
      for (file in files) {
        val target = dir.resolve(file.path)
        if (!target.normalize().startsWith(dir.normalize())) throw IOException("${file.path} is outside of $dir")
        if (!overwrite && Files.exists(target)) continue
        createParents(dir, target.parent, created)
        val existed = Files.exists(target)
        Files.write(target, file.content)
        if (!existed) created.add(target)
        written.add(target)
        if (file.executable) makeExecutable(target)
      }
    } catch (e: IOException) {
      for (path in created.asReversed()) runCatching { Files.deleteIfExists(path) }
      throw e
    }
    return written
  }

  private fun createParents(root: Path, parent: Path, created: MutableList<Path>) {
    val missing = ArrayList<Path>()
    var current: Path? = parent
    while (current != null && current.startsWith(root) && !Files.exists(current)) {
      missing.add(current)
      current = current.parent
    }
    for (folder in missing.asReversed()) {
      Files.createDirectory(folder)
      created.add(folder)
    }
  }

  private fun makeExecutable(path: Path) {
    val view = Files.getFileAttributeView(path, PosixFileAttributeView::class.java)
    if (view == null) {
      path.toFile().setExecutable(true, false)
      return
    }
    val perms = view.readAttributes().permissions().toMutableSet()
    perms += PosixFilePermission.OWNER_EXECUTE
    perms += PosixFilePermission.GROUP_EXECUTE
    perms += PosixFilePermission.OTHERS_EXECUTE
    view.setPermissions(perms)
  }
}
