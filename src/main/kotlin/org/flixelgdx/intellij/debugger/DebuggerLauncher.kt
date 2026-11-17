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

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.components.service
import java.nio.file.Path

/** What [DebuggerLauncher.ensureRunning] did. */
sealed interface LaunchOutcome {
  /** No debugger was found, so nothing was started. */
  data object NotInstalled : LaunchOutcome

  /** A debugger was already running, so nothing was started. */
  data class AlreadyRunning(val path: Path) : LaunchOutcome

  /** The debugger was started. It runs on its own and is not waited for. */
  data class Started(val path: Path) : LaunchOutcome

  /** The debugger was found but could not be started. [reason] says why. */
  data class Failed(val path: Path, val reason: String) : LaunchOutcome
}

/**
 * Tells whether a debugger is running by looking at the command of each process.
 *
 * The check is by program name, because the debugger is a single executable with a distinctive name and may be
 * started from a launcher link, a desktop entry or the terminal. It touches no IDE state, so tests can feed it plain
 * text.
 */
object DebuggerProcesses {

  /**
   * Whether any of [commands] (full paths of running programs, `null` for processes whose path is hidden) is the
   * debugger.
   *
   * @param commands the executable path of each running process.
   * @param executable the debugger the plugin would start.
   * @param realName the file name that [executable] points at after following links, or `null` when unknown. A
   * launcher link is named `flixelgdx-debugger`, but the running program is `FlixelDebugger.App`.
   */
  fun isRunning(commands: Sequence<String?>, executable: Path, realName: String? = null): Boolean {
    val names = HashSet<String>()
    executable.fileName?.toString()?.let { names += it.lowercase() }
    realName?.let { names += it.lowercase() }
    names += DebuggerDetector.EXECUTABLE.lowercase()
    names += "${DebuggerDetector.EXECUTABLE}.exe".lowercase()
    for (command in commands) {
      if (command.isNullOrEmpty()) continue
      val name = command.substringAfterLast('/').substringAfterLast('\\').lowercase()
      if (name in names) return true
    }
    return false
  }
}

/**
 * Starts the FlixelGDX Debugger, unless it is already running.
 *
 * Think of it as a doorman: it checks whether the guest is already inside before opening the door again. The debugger
 * is a separate program that keeps running after the IDE asks for it, so it is started detached and never waited for.
 *
 * Detection and starting touch the disk and the process list, so call it off the EDT.
 */
object DebuggerLauncher {

  /** Whether a process of [debugger] is running right now. */
  fun isRunning(debugger: Path): Boolean {
    val realName = runCatching { debugger.toRealPath().fileName?.toString() }.getOrNull()
    val commands = ProcessHandle.allProcesses().map { it.info().command().orElse(null) }
    return commands.use { DebuggerProcesses.isRunning(it.iterator().asSequence(), debugger, realName) }
  }

  /** Finds the debugger with [DebuggerLocator] and starts it when it is not running. */
  fun ensureRunning(): LaunchOutcome {
    val debugger = service<DebuggerLocator>().find() ?: return LaunchOutcome.NotInstalled
    if (isRunning(debugger.path)) return LaunchOutcome.AlreadyRunning(debugger.path)
    return start(debugger.path)
  }

  /** Starts [path] detached. The output is drained in the background so the program never blocks on a full pipe. */
  fun start(path: Path): LaunchOutcome {
    val cmd = GeneralCommandLine(path.toString())
    path.parent?.let { cmd.withWorkDirectory(it.toFile()) }
    return try {
      val handler = OSProcessHandler(cmd)
      handler.setShouldDestroyProcessRecursively(false)
      handler.startNotify()
      runCatching { handler.processInput?.close() }
      LaunchOutcome.Started(path)
    } catch (e: ExecutionException) {
      LaunchOutcome.Failed(path, e.message ?: "unknown error")
    }
  }
}
