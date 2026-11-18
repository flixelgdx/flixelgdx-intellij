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
package org.flixelgdx.intellij.toml

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import org.toml.lang.psi.TomlFile
import java.nio.file.Path

/**
 * A planned change to one file: [plan] reads the file and returns the [TextEdit]s to make.
 *
 * @property path the file to change.
 * @property plan reads the PSI of the file (inside the write action) and returns the edits; an empty list changes
 * nothing.
 */
class FileChange(val path: Path, val plan: (PsiFile) -> List<TextEdit>) {

  companion object {
    /** A change to a TOML file. Nothing changes when the file is not TOML. */
    fun toml(path: Path, plan: (TomlFile) -> List<TextEdit>): FileChange =
      FileChange(path) { file -> (file as? TomlFile)?.let(plan).orEmpty() }

    /** A change planned from the plain text of a file, for files whose language the plugin does not parse. */
    fun text(path: Path, plan: (String) -> List<TextEdit>): FileChange = FileChange(path) { plan(it.text) }
  }
}

/**
 * Reads the files of a game through the IDE and applies planned [TextEdit]s to them as one undoable command.
 *
 * Going through the IDE's documents instead of writing to disk directly means an open editor shows the change at
 * once, Edit > Undo can take it back, and the files are saved so Gradle reads the new text on its next sync.
 *
 * Example:
 * ```
 * val catalog = dir.resolve(VersionCatalog.PATH)
 * DocumentEdits.edit(project, "Add Library", listOf(FileChange.toml(catalog) { file ->
 *   VersionCatalog(file).addLibrary(alias, coordinates, "core")
 * }))
 * ```
 */
object DocumentEdits {

  /**
   * Reads the TOML file at [path] with [read] inside a read action.
   *
   * @return what [read] returned, or `null` when the file is not known to the IDE or is not a TOML file.
   */
  fun <T> readToml(project: Project, path: Path, read: (TomlFile) -> T): T? {
    val vf = virtualFile(path) ?: return null
    return ReadAction.compute<T?, RuntimeException> {
      (PsiManager.getInstance(project).findFile(vf) as? TomlFile)?.let(read)
    }
  }

  /**
   * Applies [changes] as one command called [title], then saves the changed files. Files that are missing are
   * skipped. Call it on the EDT.
   *
   * @return `true` when at least one file changed.
   */
  fun edit(project: Project, title: String, changes: List<FileChange>): Boolean {
    val files = changes.mapNotNull { change -> virtualFile(change.path)?.let { it to change } }
    if (files.isEmpty()) return false
    return WriteCommandAction.writeCommandAction(project).withName(title).compute<Boolean, RuntimeException> {
      val psi = PsiManager.getInstance(project)
      val documents = PsiDocumentManager.getInstance(project)
      var changed = false
      for ((vf, change) in files) {
        val file = psi.findFile(vf) ?: continue
        val document = documents.getDocument(file) ?: continue
        documents.commitDocument(document)
        val edits = change.plan(file)
        if (edits.isEmpty()) continue
        TomlEdits.apply(document, edits)
        documents.commitDocument(document)
        FileDocumentManager.getInstance().saveDocument(document)
        changed = true
      }
      changed
    }
  }

  private fun virtualFile(path: Path): VirtualFile? = LocalFileSystem.getInstance().findFileByNioFile(path)
}
