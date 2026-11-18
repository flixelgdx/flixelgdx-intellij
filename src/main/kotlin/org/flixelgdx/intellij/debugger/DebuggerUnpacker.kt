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
package org.flixelgdx.intellij.debugger

import com.intellij.openapi.util.io.NioFiles
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile

/**
 * Unpacks a downloaded debugger and swaps it into its folder, so a failed install never leaves a half-broken app.
 *
 * Think of it as moving into a new flat: everything is unpacked in a spare room next door first. Only when every box
 * is unpacked and the program is there is the old flat moved aside and the new one moved in; if that fails, the old
 * one is moved back.
 *
 * It touches the disk, so call it off the EDT.
 */
object DebuggerUnpacker {

  /** The most bytes an archive may unpack to, which stops a zip bomb but fits a self-contained app. */
  const val MAX_BYTES = 1L shl 30

  /** The most entries an archive may have. */
  const val MAX_FILES = 10_000

  /**
   * Unpacks [zip] into a fresh folder next to [dir], checks that it holds [executableName], and swaps it in.
   *
   * @throws IOException when the archive is broken, too big, has an entry that would land outside the folder, does
   * not hold the program, or when the folders cannot be swapped.
   */
  fun install(zip: Path, dir: Path, executableName: String) {
    val parent = dir.toAbsolutePath().parent ?: throw IOException("$dir has no parent folder")
    Files.createDirectories(parent)
    val stage = Files.createTempDirectory(parent, ".${dir.fileName}-new-")
    try {
      extract(zip, stage)
      val exe = stage.resolve(executableName)
      if (!Files.isRegularFile(exe)) throw IOException("The download does not contain $executableName.")
      exe.toFile().setExecutable(true, false)
      swap(stage, dir)
    } finally {
      if (Files.exists(stage)) runCatching { NioFiles.deleteRecursively(stage) }
    }
  }

  /**
   * Unpacks every entry of [zip] into [target], refusing entries whose names would leave it.
   *
   * Entries are written as plain files and folders. Java cannot see which zip entries are symbolic links, so such an
   * entry would become a small text file holding the link target, which can never point outside [target].
   */
  fun extract(zip: Path, target: Path) {
    val root = target.toAbsolutePath().normalize()
    ZipFile(zip.toFile()).use { archive ->
      if (archive.size() > MAX_FILES) throw IOException("The archive has more than $MAX_FILES entries.")
      var remaining = MAX_BYTES
      for (entry in archive.entries()) {
        val name = entry.name.replace('\\', '/').trimStart('/')
        if (name.isEmpty()) continue
        if (name.split('/').any { it == ".." } || name.contains(':')) {
          throw IOException("The archive entry ${entry.name} would be unpacked outside the install folder.")
        }
        val out = root.resolve(name).normalize()
        if (!out.startsWith(root)) throw IOException("The archive entry ${entry.name} is outside the install folder.")
        if (entry.isDirectory) {
          Files.createDirectories(out)
          continue
        }
        out.parent?.let { Files.createDirectories(it) }
        archive.getInputStream(entry).use { input ->
          Files.newOutputStream(out).use { output ->
            val buffer = ByteArray(BUFFER)
            while (true) {
              val n = input.read(buffer)
              if (n < 0) break
              remaining -= n
              if (remaining < 0) throw IOException("The archive unpacks to more than $MAX_BYTES bytes.")
              output.write(buffer, 0, n)
            }
          }
        }
      }
    }
  }

  /** Moves [stage] to [dir]. An existing [dir] is moved aside first and put back if the move fails. */
  private fun swap(stage: Path, dir: Path) {
    var old: Path? = null
    if (Files.exists(dir)) {
      old = dir.resolveSibling(".${dir.fileName}-old-${System.nanoTime()}")
      try {
        Files.move(dir, old, StandardCopyOption.ATOMIC_MOVE)
      } catch (e: IOException) {
        throw IOException("Cannot replace $dir. Is the debugger still running? (${e.message})", e)
      }
    }
    try {
      Files.move(stage, dir, StandardCopyOption.ATOMIC_MOVE)
    } catch (e: IOException) {
      old?.let { runCatching { Files.move(it, dir, StandardCopyOption.ATOMIC_MOVE) } }
      throw IOException("Cannot move the new install into $dir: ${e.message}", e)
    }
    old?.let { runCatching { NioFiles.deleteRecursively(it) } }
  }

  private const val BUFFER = 64 * 1024
}

/**
 * Creates the launchers that start the debugger outside the IDE: a `flixelgdx-debugger` link in `~/.local/bin` on
 * Linux and macOS, and a menu entry on Linux. Windows shortcuts need system APIs the plugin does not use, so on Windows
 * the debugger is started from the FlixelGDX menu or its folder.
 *
 * A launcher that cannot be created is only a note, because the program itself is installed and works.
 */
object DebuggerLaunchers {

  /** The title used for the menu entry. */
  const val TITLE = "FlixelGDX Debugger"

  private const val COMMENT = "Inspect and debug running FlixelGDX games"
  private val ICONS = listOf("flixelgdx-debugger.png", "flixelgdx-debugger.svg", "icon.png", "icon.svg")

  /**
   * Creates the launchers for [exe], installed in [dir].
   *
   * @return notes for the person, empty when everything worked.
   */
  fun create(dir: Path, exe: Path, env: DebuggerEnvironment): List<String> {
    if (env.windows) return emptyList()
    val notes = ArrayList<String>()
    notes += link(exe, env)
    if (!env.mac) notes += menuEntry(dir, exe, env)
    return notes
  }

  /** The text of a freedesktop menu entry that starts [exe], with [icon] when it is not `null`. */
  fun desktopEntry(exe: Path, icon: Path?): String = buildString {
    append("[Desktop Entry]\n")
    append("Type=Application\n")
    append("Name=$TITLE\n")
    append("Comment=$COMMENT\n")
    append("Exec=").append(quoteExec(exe.toString())).append('\n')
    if (icon != null) append("Icon=").append(icon).append('\n')
    append("Terminal=false\n")
    append("Categories=Development;\n")
  }

  private fun link(exe: Path, env: DebuggerEnvironment): List<String> {
    val bin = DebuggerPaths.binDir(env)
    val link = bin.resolve(DebuggerDetector.COMMAND)
    return try {
      Files.createDirectories(bin)
      if (Files.exists(link, LinkOption.NOFOLLOW_LINKS)) {
        if (!Files.isSymbolicLink(link)) return listOf("$link already exists and is not a link, so it was left alone.")
        Files.delete(link)
      }
      Files.createSymbolicLink(link, exe)
      val onPath = env.pathVariable.orEmpty().split(File.pathSeparatorChar)
        .any { it.isNotEmpty() && runCatching { Path.of(it).normalize() == bin.normalize() }.getOrDefault(false) }
      if (onPath) emptyList() else listOf("$bin is not on your PATH. Add it to run ${DebuggerDetector.COMMAND}.")
    } catch (e: IOException) {
      listOf("Could not create $link: ${e.message}")
    } catch (e: UnsupportedOperationException) {
      listOf("Could not create $link: ${e.message}")
    }
  }

  private fun menuEntry(dir: Path, exe: Path, env: DebuggerEnvironment): List<String> {
    val apps = DebuggerPaths.dataHome(env).resolve("applications")
    val icon = ICONS.map { dir.resolve(it) }.firstOrNull { Files.isRegularFile(it) }
    return try {
      Files.createDirectories(apps)
      Files.writeString(apps.resolve("${DebuggerDetector.COMMAND}.desktop"), desktopEntry(exe, icon))
      emptyList()
    } catch (e: IOException) {
      listOf("Could not create the menu entry: ${e.message}")
    }
  }

  /** Quotes a path for the Exec key, where a space or quote would otherwise split the command. */
  private fun quoteExec(path: String): String {
    if (path.none { it in " \t\n\"'\\><~|&;$*?#()`%" }) return path
    val escaped = path.replace("\\", "\\\\").replace("\"", "\\\"").replace("`", "\\`")
      .replace("$", "\\$").replace("%", "%%")
    return "\"$escaped\""
  }
}
